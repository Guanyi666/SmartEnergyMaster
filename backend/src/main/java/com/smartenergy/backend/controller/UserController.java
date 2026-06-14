package com.smartenergy.backend.controller;

import com.smartenergy.backend.dto.UserUpsertRequest;
import com.smartenergy.backend.service.UserService;
import com.smartenergy.backend.vo.PageVO;
import com.smartenergy.backend.vo.UserVO;
import com.smartenergy.backend.vo.UserWithPersonnelVO;
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
                               @RequestParam(required = false) String department) {
        return userService.listUsers(page, size, keyword, role, department);
    }

    @GetMapping("/with-personnel")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'HR_MANAGER', 'DEVICE_MANAGER', 'MANAGER')")
    public PageVO<UserWithPersonnelVO> listWithPersonnel(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) Boolean isMaintenance) {
        return userService.listUsersWithPersonnel(page, size, keyword, role, department, isMaintenance);
    }

    @PostMapping
    public UserVO create(@Valid @RequestBody UserUpsertRequest request) {
        return userService.createUser(request);
    }

    @PutMapping("/{id}")
    public UserVO update(@PathVariable Integer id, @Valid @RequestBody UserUpsertRequest request) {
        return userService.updateUser(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Integer id) {
        userService.deleteUser(id);
    }
}
