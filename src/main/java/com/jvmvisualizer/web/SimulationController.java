package com.jvmvisualizer.web;

import com.jvmvisualizer.engine.SimulationEngine;
import com.jvmvisualizer.model.MemoryState;
import com.jvmvisualizer.parser.CodeParser;
import com.jvmvisualizer.parser.ParseException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Web equivalent of {@link com.jvmvisualizer.ui.MainController}: parses
 * Playground code and runs it through the same {@link SimulationEngine}
 * used by the desktop app, returning the resulting timeline as JSON.
 *
 * Stateless by design — the server holds no session between requests, so a
 * GC step is appended by re-running the script server-side rather than
 * trusting a client-supplied timeline (which could otherwise be forged to
 * request GC against a heap state the server never produced).
 */
@RestController
public class SimulationController {

    private final CodeParser parser = new CodeParser();
    private final SimulationEngine engine = new SimulationEngine();

    @PostMapping("/api/simulate")
    public List<MemoryStateView> simulate(@RequestBody SimulateRequest request) throws ParseException {
        List<MemoryState> timeline = engine.run(parser.parse(request.code));
        return toViews(timeline);
    }

    /** Re-runs the script, then appends a garbage-collection step to the resulting timeline. */
    @PostMapping("/api/simulate-with-gc")
    public List<MemoryStateView> simulateWithGc(@RequestBody SimulateRequest request) throws ParseException {
        List<MemoryState> timeline = new ArrayList<>(engine.run(parser.parse(request.code)));
        engine.appendGarbageCollection(timeline);
        return toViews(timeline);
    }

    private List<MemoryStateView> toViews(List<MemoryState> timeline) {
        return timeline.stream().map(MemoryStateView::from).collect(Collectors.toList());
    }

    @ExceptionHandler(ParseException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ParseErrorResponse handleParseException(ParseException ex) {
        ParseErrorResponse response = new ParseErrorResponse();
        response.message = ex.getMessage();
        response.lineNumber = ex.getLineNumber();
        return response;
    }

    public static class ParseErrorResponse {
        public String message;
        public int lineNumber;
    }
}
