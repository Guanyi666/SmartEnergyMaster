package com.smartenergy.backend.controller;

import com.smartenergy.backend.dto.UserUpsertRequest;
import com.smartenergy.backend.service.UserService;
import com.smartenergy.backend.vo.PageVO;
import com.smartenergy.backend.vo.UserVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('ADMIN', 'HR_MANAGER')")
@Tag(name = "用户管理", description = "人事与管理员用户档案管理")
public class UserController {

    private final UserService userService;

    @GetMapping
    public PageVO<UserVO> list(@RequestParam(defaultValue = "1") int page,
                               @RequestParam(defaultValue = "10") int size,
                               @RequestParam(required = false) String keyword,
                               @RequestParam(required = false) String role,
                               @RequestParam(required = false) String department,
                               @RequestParam(required = false) String status) {
        return userService.listUsers(page, size, keyword, role, department, status);
    }

    @PostMapping
    public UserVO create(@Valid @RequestBody UserUpsertRequest request) {
        return userService.createUser(request);
    }

    @PutMapping("/{id}")
    public UserVO update(@PathVariable Integer id, @Valid @RequestBody UserUpsertRequest request) {
        return userService.updateUser(id, request);
    }

    @PatchMapping("/{id}/status")
    public UserVO updateStatus(@PathVariable Integer id, @RequestParam String status) {
        return userService.updateStatus(id, status);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Integer id) {
        userService.deleteUser(id);
    }
}
