package io.myclaw.tools;

import io.myclaw.core.json.Json;
import io.myclaw.core.tool.Tool;
import io.myclaw.core.tool.ToolContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GitToolsTest {
    @TempDir Path repository;
    ToolContext context;
    String initialBranch;

    @BeforeEach
    void initializeRepository() throws Exception {
        context = ToolContext.of(repository);
        git("init");
        git("config", "user.name", "MyClaw Test");
        git("config", "user.email", "myclaw@example.test");
        Files.writeString(repository.resolve("a.txt"), "one\n");
        git("add", "a.txt");
        git("commit", "-m", "initial");
        initialBranch = git("branch", "--show-current").strip();
    }

    @Test
    void statusDiffAddResetCommitAndLog() throws Exception {
        Files.writeString(repository.resolve("a.txt"), "two\n");
        assertThat(call(GitTools.status(), Json.obj())).contains("a.txt");
        assertThat(call(GitTools.diff(), Json.obj().put("path", "a.txt"))).contains("-one", "+two");

        call(GitTools.add(), Json.obj().set("paths", Json.arr().add("a.txt")));
        assertThat(call(GitTools.diff(), Json.obj().put("staged", true))).contains("+two");
        call(GitTools.reset(), Json.obj().set("paths", Json.arr().add("a.txt")));
        assertThat(call(GitTools.diff(), Json.obj())).contains("+two");

        call(GitTools.add(), Json.obj().set("paths", Json.arr().add("a.txt")));
        call(GitTools.commit(), Json.obj().put("message", "update a"));
        assertThat(call(GitTools.log(), Json.obj().put("limit", 1))).contains("update a");
    }

    @Test
    void createsAndSwitchesBranchesWithoutForce() throws Exception {
        call(GitTools.checkout(), Json.obj().put("branch", "feature/safe").put("create", true));
        assertThat(call(GitTools.branch(), Json.obj().put("action", "list"))).contains("feature/safe");
        call(GitTools.checkout(), Json.obj().put("branch", initialBranch));
        call(GitTools.branch(), Json.obj().put("action", "delete").put("name", "feature/safe"));
        assertThat(call(GitTools.branch(), Json.obj().put("action", "list"))).doesNotContain("feature/safe");
    }

    @Test
    void rejectsTraversalAndOptionLikeBranchNames() {
        assertThatThrownBy(() -> call(GitTools.add(), Json.obj().set("paths", Json.arr().add("../outside"))))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> call(GitTools.checkout(), Json.obj().put("branch", "--detach")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private String call(Tool tool, tools.jackson.databind.JsonNode arguments) throws Exception {
        return tool.call(arguments, context);
    }


    private String git(String... arguments) throws Exception {
        List<String> command = new java.util.ArrayList<>();
        command.add("git"); command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).directory(repository.toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        if (process.waitFor() != 0) throw new IllegalStateException(output);
        return output;
    }
}
