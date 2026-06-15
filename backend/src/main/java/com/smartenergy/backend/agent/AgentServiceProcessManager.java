package com.smartenergy.backend.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Starts the Python FastAPI Agent service together with the Spring Boot backend
 * during local development. If :8001 is already healthy, the existing process is reused.
 */
@Slf4j
@Component
public class AgentServiceProcessManager implements ApplicationRunner, DisposableBean {

    @Value("${agent.service.auto-start:true}")
    private boolean autoStart;

    @Value("${agent.service.base-url:http://localhost:8001}")
    private String baseUrl;

    @Value("${agent.service.host:0.0.0.0}")
    private String host;

    @Value("${agent.service.port:8001}")
    private int port;

    @Value("${agent.service.python:${AGENT_PYTHON:python}}")
    private String pythonExecutable;

    @Value("${agent.service.working-dir:../ai_models}")
    private String workingDir;

    @Value("${agent.service.startup-timeout-ms:20000}")
    private long startupTimeoutMs;

    @Value("${agent.service.backend-base-url:http://localhost:8080/api}")
    private String backendBaseUrl;

    @Value("${prediction.service.base-url:http://localhost:8000}")
    private String predictionBaseUrl;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    private Process process;

    @Override
    public void run(ApplicationArguments args) {
        if (!autoStart) {
            log.info("[Agent] agent.service.auto-start=false, 跳过 Python Agent 自动启动");
            return;
        }
        if (isHealthy()) {
            log.info("[Agent] Python Agent 服务已可用: {}", baseUrl);
            return;
        }

        Path cwd = resolveWorkingDirectory();
        if (cwd == null) {
            log.warn("[Agent] 未找到 ai_models/agent/agent_service.py, 无法自动启动 Python Agent.");
            log.warn("[Agent] 请设置 AGENT_WORKING_DIR 环境变量指向 ai_models 目录，或手动启动: cd ai_models && python -m uvicorn agent.agent_service:app --host 0.0.0.0 --port 8001");
            return;
        }

        // 尝试多个 Python 可执行文件名
        String[] pythonCandidates = {pythonExecutable, "python3", "py", "python"};
        boolean started = false;
        for (String pyCandidate : pythonCandidates) {
            if (started) break;
            List<String> command = new ArrayList<>();
            command.add(pyCandidate);
            command.add("-m");
            command.add("uvicorn");
            command.add("agent.agent_service:app");
            command.add("--host");
            command.add(host);
            command.add("--port");
            command.add(String.valueOf(port));

            try {
                ProcessBuilder builder = new ProcessBuilder(command);
                builder.directory(cwd.toFile());
                builder.redirectErrorStream(true);
                Map<String, String> env = builder.environment();
                env.putIfAbsent("BACKEND_BASE_URL", backendBaseUrl);
                env.putIfAbsent("PREDICTION_BASE_URL", predictionBaseUrl);

                log.info("[Agent] 尝试启动: {} (cwd={})", String.join(" ", command), cwd);
                process = builder.start();
                startLogPump(process);

                if (waitUntilHealthy()) {
                    log.info("[Agent] Python Agent 服务启动成功 ({}): {}", pyCandidate, baseUrl);
                    started = true;
                } else if (!process.isAlive()) {
                    log.warn("[Agent] Python Agent 进程已退出 ({}), 尝试下一个 Python 可执行文件...", pyCandidate);
                    process = null;
                } else {
                    // 进程存活但健康检查未通过 — 保持进程，可能是启动慢
                    log.info("[Agent] Python Agent 进程运行中但健康检查未通过 ({}), 保持进程继续等待", pyCandidate);
                    started = true;
                }
            } catch (IOException e) {
                log.debug("[Agent] {} 不可用: {}", pyCandidate, e.getMessage());
                process = null;
            }
        }

        if (!started && process == null) {
            log.warn("[Agent] 所有 Python 可执行文件尝试均失败.");
            log.warn("[Agent] 请确认: 1) Python 已安装且在 PATH 中  2) 已执行: pip install -r ai_models/agent/requirements.txt");
            log.warn("[Agent] 或手动启动 Agent: cd ai_models && python -m uvicorn agent.agent_service:app --host 0.0.0.0 --port 8001");
        }
    }

    private boolean isHealthy() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/health"))
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean waitUntilHealthy() {
        long deadline = System.currentTimeMillis() + startupTimeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (isHealthy()) {
                return true;
            }
            if (process != null && !process.isAlive()) {
                return false;
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private Path resolveWorkingDirectory() {
        Path configured = Paths.get(workingDir);
        if (!configured.isAbsolute()) {
            configured = Paths.get(System.getProperty("user.dir")).resolve(configured).normalize();
        }
        if (isAgentRoot(configured)) {
            return configured;
        }

        Path userDir = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        List<Path> candidates = new ArrayList<>();
        candidates.add(userDir.resolve("ai_models"));
        candidates.add(userDir.resolve("../ai_models").normalize());
        if (userDir.getParent() != null) {
            candidates.add(userDir.getParent().resolve("ai_models"));
        }

        for (Path candidate : candidates) {
            if (isAgentRoot(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private boolean isAgentRoot(Path path) {
        return Files.isRegularFile(path.resolve("agent").resolve("agent_service.py"));
    }

    private void startLogPump(Process startedProcess) {
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(startedProcess.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.info("[Agent] {}", line);
                }
            } catch (IOException e) {
                log.debug("[Agent] 日志读取结束: {}", e.getMessage());
            }
        }, "python-agent-log");
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public void destroy() {
        if (process == null || !process.isAlive()) {
            return;
        }
        log.info("[Agent] 正在停止由后端启动的 Python Agent 服务");
        process.destroy();
        try {
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }
}
