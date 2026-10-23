package com.jvmvisualizer.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the web build. Separate from {@link com.jvmvisualizer.app.App}
 * (the JavaFX desktop entry point) so the same parser/engine/model code can be
 * driven by either a desktop GUI or a browser, depending on which is launched.
 */
@SpringBootApplication
public class WebApp {
    public static void main(String[] args) {
        SpringApplication.run(WebApp.class, args);
    }
}
