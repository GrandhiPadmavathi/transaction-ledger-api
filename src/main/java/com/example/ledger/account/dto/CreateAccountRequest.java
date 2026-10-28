package com.example.ledger.account.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public class CreateAccountRequest {

    @NotBlank(message = "name is required")
    @Size(max = 255, message = "name must be at most 255 characters")
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
