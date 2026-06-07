package com.smartenergy.backend.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DispatchAdviceVO {

    private String level;

    private String title;

    private String content;
}
