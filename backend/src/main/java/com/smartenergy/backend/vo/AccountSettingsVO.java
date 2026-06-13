package com.smartenergy.backend.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AccountSettingsVO {
    private String username;
    private String phone;
    private String email;
    private boolean passwordChanged;
}
