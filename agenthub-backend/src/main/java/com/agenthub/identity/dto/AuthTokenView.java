package com.agenthub.identity.dto;

public class AuthTokenView {
    private String token;
    private String tokenType;
    private long expiresIn;
    private UserView user;

    public AuthTokenView() {}

    public AuthTokenView(String token, String tokenType, long expiresIn, UserView user) {
        this.token = token;
        this.tokenType = tokenType;
        this.expiresIn = expiresIn;
        this.user = user;
    }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getTokenType() { return tokenType; }
    public void setTokenType(String tokenType) { this.tokenType = tokenType; }
    public long getExpiresIn() { return expiresIn; }
    public void setExpiresIn(long expiresIn) { this.expiresIn = expiresIn; }
    public UserView getUser() { return user; }
    public void setUser(UserView user) { this.user = user; }
}
