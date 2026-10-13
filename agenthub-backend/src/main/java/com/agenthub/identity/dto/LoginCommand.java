package com.agenthub.identity.dto;

import com.agenthub.shared.contract.BoundedContext;
import com.agenthub.shared.contract.ContextCommand;

public class LoginCommand implements ContextCommand {
    private String email;
    private String password;

    public LoginCommand() {}

    public LoginCommand(String email, String password) {
        this.email = email;
        this.password = password;
    }

    @Override
    public BoundedContext boundedContext() {
        return BoundedContext.IDENTITY;
    }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
