package com.cloudnote.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Step 10 of the deploy plan: writes ERROR lines on purpose so you can test the CloudWatch alarm. */
@RestController
@RequestMapping("/api/debug")
@ConditionalOnProperty(name = "cloudnote.debug-endpoints", havingValue = "true")
public class DebugController {

    private static final Logger log = LoggerFactory.getLogger(DebugController.class);

    @GetMapping("/error")
    public String error(@RequestParam(defaultValue = "1") int count) {
        int n = Math.max(1, Math.min(count, 50));
        for (int i = 1; i <= n; i++) {
            log.error("Test error {} of {} triggered from /api/debug/error", i, n);
        }
        return "Logged " + n + " ERROR line(s)";
    }
}
