package com.smartenergy.backend.vo;

/**
 * @author Duan Guanyi
 * @version 1.0.0
 * @date 2026/3/18
 */

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登陆成功后返回的视图对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginVO {
    private String token;

    private String username;

    private String role;
}
