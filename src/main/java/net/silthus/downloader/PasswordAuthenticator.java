package net.silthus.downloader;

import java.net.Authenticator;
import java.net.PasswordAuthentication;

public class PasswordAuthenticator extends Authenticator {

    private String username, password;

    public PasswordAuthenticator(String user, String pass) {
        username = user;
        password = pass;
    }

    protected PasswordAuthentication getPasswordAuthentication() {
        return new PasswordAuthentication(username, password.toCharArray());
    }
}
