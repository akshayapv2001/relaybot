package dev.relaybot.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Angular handles these routes in the browser; a hard refresh must still return index.html. */
@Controller
public class SpaController {

    @GetMapping({"/", "/login", "/activity", "/reports", "/jobs", "/servers", "/servers/{id}"})
    public String index() {
        return "forward:/index.html";
    }
}
