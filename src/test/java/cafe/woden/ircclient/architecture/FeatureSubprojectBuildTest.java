package cafe.woden.ircclient.architecture;

import static cafe.woden.ircclient.architecture.JavaSourceText.containsIgnoringWhitespace;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Build scripts and resources are checked here; Java boundaries use compiled classes. */
class FeatureSubprojectBuildTest {

  private static final Pattern FEATURE_INCLUDE_PATTERN =
      Pattern.compile("(?m)^\\s*include\\s+['\"](ircafe-feature-[\\w-]+)['\"]\\s*$");

  @Test
  void featureSubprojectsAreIncludedByTheRootApp() throws IOException {
    String build = Files.readString(Path.of("build.gradle"));

    for (String projectName : featureProjectNames()) {
      assertTrue(
          build.contains("implementation project(':" + projectName + "')")
              || build.contains("runtimeOnly project(':" + projectName + "')"),
          "the root app should include "
              + projectName
              + " so feature Spring beans are available at runtime");
    }
  }

  @Test
  void ircv3FeatureSubprojectsApplySharedBuildConvention() throws IOException {
    for (String projectName : featureProjectNames()) {
      if (!projectName.startsWith("ircafe-feature-ircv3-")) {
        continue;
      }
      Path buildFile = Path.of(projectName, "build.gradle");
      String build = Files.readString(buildFile);
      assertTrue(
          build.contains("apply from: rootProject.file('gradle/ircv3-feature-conventions.gradle')"),
          buildFile + " should apply the shared IRCv3 feature convention");
      assertTrue(
          !build.contains("JavaLanguageVersion.of")
              && !build.contains("springBootStarterTest")
              && !build.contains("cyclonedxDirectBom"),
          buildFile + " should not repeat shared Java, test, or CycloneDX setup");
    }
  }

  @Test
  void featureSubprojectsDoNotDeclareServiceLoaderProviders() throws IOException {
    Set<String> violations = new TreeSet<>();
    for (Path projectDir : featureProjectDirs()) {
      Path servicesDir = projectDir.resolve("src/main/resources/META-INF/services");
      if (Files.exists(servicesDir)) {
        try (Stream<Path> files = Files.walk(servicesDir)) {
          files
              .filter(Files::isRegularFile)
              .sorted()
              .forEach(path -> violations.add(path.toString()));
        }
      }
    }

    assertTrue(
        violations.isEmpty(),
        () ->
            "Feature subprojects own Spring/runtime behavior; ServiceLoader provider data belongs "
                + "in ircafe-builtins-* jars. Violations:\n  "
                + String.join("\n  ", violations));
  }

  @Test
  void ircv3FeatureFamilySplitUsesFocusedProjects() throws IOException {
    Path settings = Path.of("settings.gradle");
    Path rootBuild = Path.of("build.gradle");
    Path commonBuild = Path.of("ircafe-feature-ircv3-common/build.gradle");
    Path negotiationBuild = Path.of("ircafe-feature-ircv3-negotiation/build.gradle");
    Path messageTagsBuild = Path.of("ircafe-feature-ircv3-message-tags/build.gradle");
    Path serverTimeBuild = Path.of("ircafe-feature-ircv3-server-time/build.gradle");
    Path echoMessageBuild = Path.of("ircafe-feature-ircv3-echo-message/build.gradle");
    Path labeledResponseBuild = Path.of("ircafe-feature-ircv3-labeled-response/build.gradle");
    Path multilineBuild = Path.of("ircafe-feature-ircv3-multiline/build.gradle");
    Path chatHistoryBuild = Path.of("ircafe-feature-ircv3-chat-history/build.gradle");
    Path replyBuild = Path.of("ircafe-feature-ircv3-reply/build.gradle");
    Path reactionsBuild = Path.of("ircafe-feature-ircv3-reactions/build.gradle");
    Path channelContextBuild = Path.of("ircafe-feature-ircv3-channel-context/build.gradle");
    Path typingBuild = Path.of("ircafe-feature-ircv3-typing/build.gradle");
    Path readMarkerBuild = Path.of("ircafe-feature-ircv3-read-marker/build.gradle");
    Path redactionBuild = Path.of("ircafe-feature-ircv3-message-redaction/build.gradle");
    Path messageEditBuild = Path.of("ircafe-feature-ircv3-message-edit/build.gradle");
    Path saslBuild = Path.of("ircafe-feature-ircv3-sasl/build.gradle");
    Path stsBuild = Path.of("ircafe-feature-ircv3-sts/build.gradle");
    Path awayNotifyBuild = Path.of("ircafe-feature-ircv3-away-notify/build.gradle");
    Path accountNotifyBuild = Path.of("ircafe-feature-ircv3-account-notify/build.gradle");
    Path extendedJoinBuild = Path.of("ircafe-feature-ircv3-extended-join/build.gradle");
    Path chghostBuild = Path.of("ircafe-feature-ircv3-chghost/build.gradle");
    Path setnameBuild = Path.of("ircafe-feature-ircv3-setname/build.gradle");
    Path inviteNotifyBuild = Path.of("ircafe-feature-ircv3-invite-notify/build.gradle");
    Path monitorBuild = Path.of("ircafe-feature-ircv3-monitor/build.gradle");
    Path standardRepliesBuild = Path.of("ircafe-feature-ircv3-standard-replies/build.gradle");
    Path accountTagBuild = Path.of("ircafe-feature-ircv3-account-tag/build.gradle");
    Path userIdentityBuild = Path.of("ircafe-feature-ircv3-user-identity/build.gradle");
    Path batchBuild = Path.of("ircafe-feature-ircv3-batch/build.gradle");
    Path zncPlaybackBuild = Path.of("ircafe-feature-ircv3-znc-playback/build.gradle");
    Path oldNamesBuild = Path.of("ircafe-feature-ircv3-names/build.gradle");
    Path oldHistoryTransportBuild = Path.of("ircafe-feature-ircv3-history-transport/build.gradle");
    Path oldDraftBuild = Path.of("ircafe-feature-ircv3-draft/build.gradle");
    Path oldUmbrellaBuild = Path.of("ircafe-feature-ircv3/build.gradle");

    String settingsSource = Files.readString(settings);
    assertTrue(
        settingsSource.contains("include 'ircafe-feature-ircv3-common'"),
        "the IRCv3 family should declare a deliberately small shared project");
    assertTrue(
        settingsSource.contains("include 'ircafe-feature-ircv3-negotiation'"),
        "the IRCv3 family should declare a dedicated negotiation project");
    assertTrue(
        settingsSource.contains("include 'ircafe-feature-ircv3-message-tags'"),
        "the IRCv3 family should declare a dedicated message-tags project");
    assertTrue(
        settingsSource.contains("include 'ircafe-feature-ircv3-server-time'"),
        "the IRCv3 family should declare a dedicated server-time project");
    assertTrue(
        settingsSource.contains("include 'ircafe-feature-ircv3-echo-message'"),
        "the IRCv3 family should declare a dedicated echo-message project");
    assertTrue(
        settingsSource.contains("include 'ircafe-feature-ircv3-labeled-response'"),
        "the IRCv3 family should declare a dedicated labeled-response project");
    assertTrue(
        settingsSource.contains("include 'ircafe-feature-ircv3-multiline'"),
        "the IRCv3 family should declare a dedicated multiline project");
    assertTrue(
        settingsSource.contains("include 'ircafe-feature-ircv3-chat-history'"),
        "the IRCv3 family should declare a dedicated chat-history project");
    assertTrue(
        settingsSource.contains("include 'ircafe-feature-ircv3-reply'"),
        "the IRCv3 family should declare a dedicated reply project");
    assertTrue(
        settingsSource.contains("include 'ircafe-feature-ircv3-reactions'"),
        "the IRCv3 family should declare a dedicated reactions project");
    assertTrue(
        settingsSource.contains("include 'ircafe-feature-ircv3-channel-context'"),
        "the IRCv3 family should declare a dedicated channel-context project");
    assertTrue(
        settingsSource.contains("include 'ircafe-feature-ircv3-typing'"),
        "the IRCv3 family should declare a dedicated typing project");
    assertTrue(
        settingsSource.contains("include 'ircafe-feature-ircv3-read-marker'"),
        "the IRCv3 family should declare a dedicated read-marker project");
    assertTrue(
        settingsSource.contains("include 'ircafe-feature-ircv3-message-redaction'"),
        "the IRCv3 family should declare a dedicated message-redaction project");
    assertTrue(
        settingsSource.contains("include 'ircafe-feature-ircv3-message-edit'"),
        "the IRCv3 family should declare a dedicated message-edit project");
    assertTrue(
        settingsSource.contains("include 'ircafe-feature-ircv3-sasl'"),
        "the IRCv3 family should declare a dedicated SASL project");
    assertTrue(
        settingsSource.contains("include 'ircafe-feature-ircv3-sts'"),
        "the IRCv3 family should declare a dedicated STS project");
    for (String capability :
        List.of(
            "away-notify",
            "account-notify",
            "extended-join",
            "chghost",
            "setname",
            "invite-notify")) {
      assertTrue(
          settingsSource.contains("include 'ircafe-feature-ircv3-" + capability + "'"),
          "the IRCv3 family should declare a dedicated " + capability + " project");
    }
    assertTrue(
        !settingsSource.contains("include 'ircafe-feature-ircv3-presence'"),
        "the aggregate presence project should be retired");
    assertTrue(
        settingsSource.contains("include 'ircafe-feature-ircv3-monitor'"),
        "the IRCv3 family should declare a dedicated MONITOR project");
    assertTrue(
        settingsSource.contains("include 'ircafe-feature-ircv3-standard-replies'"),
        "the IRCv3 family should declare a dedicated standard-replies project");
    assertTrue(
        settingsSource.contains("include 'ircafe-feature-ircv3-account-tag'"),
        "the IRCv3 family should declare a dedicated account-tag project");
    assertTrue(
        settingsSource.contains("include 'ircafe-feature-ircv3-user-identity'"),
        "the IRCv3 family should declare a dedicated user-identity/WHOX project");
    assertTrue(
        settingsSource.contains("include 'ircafe-feature-ircv3-batch'"),
        "the IRCv3 family should declare a dedicated BATCH project");
    assertTrue(
        settingsSource.contains("include 'ircafe-feature-ircv3-znc-playback'"),
        "the IRCv3 family should declare a dedicated ZNC playback project");
    assertTrue(
        !settingsSource.contains("include 'ircafe-feature-ircv3-history-transport'"),
        "the aggregate history-transport project should be retired");
    assertTrue(
        !settingsSource.contains("include 'ircafe-feature-ircv3-draft'"),
        "the ambiguous draft project should be removed");
    assertTrue(
        !settingsSource.contains("include 'ircafe-feature-ircv3'"),
        "the source-free compatibility aggregate should be removed");

    assertTrue(Files.isRegularFile(commonBuild), "the IRCv3 common project needs a build file");
    assertTrue(
        Files.isRegularFile(negotiationBuild), "the IRCv3 negotiation project needs a build file");
    assertTrue(
        Files.isRegularFile(messageTagsBuild), "the IRCv3 message-tags project needs a build file");
    assertTrue(
        Files.isRegularFile(serverTimeBuild), "the IRCv3 server-time project needs a build file");
    assertTrue(
        Files.isRegularFile(echoMessageBuild), "the IRCv3 echo-message project needs a build file");
    assertTrue(
        Files.isRegularFile(labeledResponseBuild),
        "the IRCv3 labeled-response project needs a build file");
    assertTrue(
        Files.isRegularFile(multilineBuild), "the IRCv3 multiline project needs a build file");
    assertTrue(
        Files.isRegularFile(chatHistoryBuild), "the IRCv3 chat-history project needs a build file");
    assertTrue(Files.isRegularFile(replyBuild), "the IRCv3 reply project needs a build file");
    assertTrue(
        Files.isRegularFile(reactionsBuild), "the IRCv3 reactions project needs a build file");
    assertTrue(
        Files.isRegularFile(channelContextBuild),
        "the IRCv3 channel-context project needs a build file");
    assertTrue(Files.isRegularFile(typingBuild), "the IRCv3 typing project needs a build file");
    assertTrue(
        Files.isRegularFile(readMarkerBuild), "the IRCv3 read-marker project needs a build file");
    assertTrue(
        Files.isRegularFile(redactionBuild),
        "the IRCv3 message-redaction project needs a build file");
    assertTrue(
        Files.isRegularFile(messageEditBuild), "the IRCv3 message-edit project needs a build file");
    assertTrue(Files.isRegularFile(saslBuild), "the IRCv3 SASL project needs a build file");
    assertTrue(Files.isRegularFile(stsBuild), "the IRCv3 STS project needs a build file");
    for (Path focusedPresenceBuild :
        List.of(
            awayNotifyBuild,
            accountNotifyBuild,
            extendedJoinBuild,
            chghostBuild,
            setnameBuild,
            inviteNotifyBuild)) {
      assertTrue(
          Files.isRegularFile(focusedPresenceBuild), focusedPresenceBuild + " needs a build file");
    }
    assertTrue(
        !Files.exists(Path.of("ircafe-feature-ircv3-presence/build.gradle")),
        "the aggregate presence project should be removed");
    assertTrue(Files.isRegularFile(monitorBuild), "the IRCv3 MONITOR project needs a build file");
    assertTrue(
        Files.isRegularFile(standardRepliesBuild),
        "the IRCv3 standard-replies project needs a build file");
    assertTrue(
        Files.isRegularFile(accountTagBuild), "the IRCv3 account-tag project needs a build file");
    assertTrue(
        Files.isRegularFile(userIdentityBuild),
        "the IRCv3 user-identity project needs a build file");
    assertTrue(Files.isRegularFile(batchBuild), "the IRCv3 BATCH project needs a build file");
    assertTrue(
        Files.isRegularFile(zncPlaybackBuild), "the IRCv3 ZNC playback project needs a build file");
    assertTrue(!Files.exists(oldNamesBuild), "the aggregate names project should be removed");
    assertTrue(
        !Files.exists(oldHistoryTransportBuild),
        "the aggregate history-transport project should be removed");
    assertTrue(!Files.exists(oldDraftBuild), "the ambiguous draft build should be removed");
    assertTrue(
        !Files.exists(oldUmbrellaBuild), "the obsolete compatibility aggregate should be removed");

    String commonSource = Files.readString(commonBuild);
    assertTrue(
        !commonSource.contains("project(':ircafe-feature-ircv3-"),
        "the IRCv3 common project must not depend on capability-family projects");

    String rootSource = Files.readString(rootBuild);
    assertTrue(
        rootSource.contains("implementation project(':ircafe-feature-ircv3-message-tags')"),
        "the root application should consume the focused message-tags runtime directly");
    assertTrue(
        rootSource.contains("implementation project(':ircafe-feature-ircv3-server-time')"),
        "the root application should consume the focused server-time runtime directly");
    assertTrue(
        rootSource.contains("implementation project(':ircafe-feature-ircv3-echo-message')"),
        "the root application should consume the focused echo-message runtime directly");
    assertTrue(
        rootSource.contains("implementation project(':ircafe-feature-ircv3-labeled-response')"),
        "the root application should consume the focused labeled-response runtime directly");
    assertTrue(
        rootSource.contains("implementation project(':ircafe-feature-ircv3-multiline')"),
        "the root application should consume the focused multiline runtime directly");
    assertTrue(
        rootSource.contains("implementation project(':ircafe-feature-ircv3-chat-history')"),
        "the root application should consume the focused chat-history runtime directly");
    assertTrue(
        rootSource.contains("implementation project(':ircafe-feature-ircv3-reply')"),
        "the root application should consume the focused reply runtime directly");
    assertTrue(
        rootSource.contains("implementation project(':ircafe-feature-ircv3-reactions')"),
        "the root application should consume the focused reactions runtime directly");
    assertTrue(
        rootSource.contains("implementation project(':ircafe-feature-ircv3-channel-context')"),
        "the root application should consume the focused channel-context runtime directly");
    assertTrue(
        rootSource.contains("implementation project(':ircafe-feature-ircv3-typing')"),
        "the root application should consume the focused typing runtime directly");
    assertTrue(
        rootSource.contains("implementation project(':ircafe-feature-ircv3-read-marker')"),
        "the root application should consume the focused read-marker runtime directly");
    assertTrue(
        rootSource.contains("implementation project(':ircafe-feature-ircv3-message-redaction')"),
        "the root application should consume the focused message-redaction runtime directly");
    assertTrue(
        rootSource.contains("implementation project(':ircafe-feature-ircv3-message-edit')"),
        "the root application should consume the focused message-edit runtime directly");
    assertTrue(
        rootSource.contains("implementation project(':ircafe-feature-ircv3-sasl')"),
        "the root application should consume the focused SASL runtime directly");
    assertTrue(
        rootSource.contains("implementation project(':ircafe-feature-ircv3-sts')"),
        "the root application should consume the focused STS runtime directly");
    for (String capability :
        List.of(
            "away-notify",
            "account-notify",
            "extended-join",
            "chghost",
            "setname",
            "invite-notify")) {
      assertTrue(
          rootSource.contains("implementation project(':ircafe-feature-ircv3-" + capability + "')"),
          "the root application should consume the focused " + capability + " runtime directly");
    }
    assertTrue(
        !rootSource.contains("implementation project(':ircafe-feature-ircv3-presence')"),
        "the root application should not consume the retired presence aggregate");
    assertTrue(
        rootSource.contains("implementation project(':ircafe-feature-ircv3-monitor')"),
        "the root application should consume the focused MONITOR runtime directly");
    assertTrue(
        rootSource.contains("implementation project(':ircafe-feature-ircv3-standard-replies')"),
        "the root application should consume the focused standard-replies runtime directly");
    assertTrue(
        rootSource.contains("implementation project(':ircafe-feature-ircv3-account-tag')"),
        "the root application should consume the focused account-tag runtime directly");
    assertTrue(
        rootSource.contains("implementation project(':ircafe-feature-ircv3-user-identity')"),
        "the root application should consume the focused user-identity runtime directly");
    assertTrue(
        rootSource.contains("implementation project(':ircafe-feature-ircv3-batch')"),
        "the root application should consume the focused BATCH runtime directly");
    assertTrue(
        rootSource.contains("implementation project(':ircafe-feature-ircv3-znc-playback')"),
        "the root application should consume the focused ZNC playback runtime directly");
    assertTrue(
        !rootSource.contains("implementation project(':ircafe-feature-ircv3-history-transport')"),
        "the root application should not consume the retired history-transport aggregate");
    assertTrue(
        !rootSource.contains("implementation project(':ircafe-feature-ircv3-draft')"),
        "the root application should not consume the ambiguous draft runtime");
    assertTrue(
        !rootSource.contains("implementation project(':ircafe-feature-ircv3')"),
        "the root application should not consume the obsolete compatibility aggregate");

    String negotiationSource = Files.readString(negotiationBuild);
    assertTrue(
        negotiationSource.contains("api project(':ircafe-feature-ircv3-common')"),
        "the negotiation project should consume shared capability values through common");
    assertTrue(
        negotiationSource.contains("api project(':ircafe-plugin-api')"),
        "provider metadata policy should continue to compile against the public plugin API");

    assertTransportIndependentFocusedBuild(messageTagsBuild, false);
    assertMessageTagSignalBuild(serverTimeBuild);
    assertMessageTagSignalBuild(echoMessageBuild);
    assertMessageTagSignalBuild(labeledResponseBuild);
    assertTrue(
        Files.readString(labeledResponseBuild)
            .contains("api project(':ircafe-feature-ircv3-common')"),
        "labeled-response should reuse shared outbound tag escaping");
    assertTrue(
        Files.readString(echoMessageBuild)
            .contains("implementation project(':ircafe-feature-ircv3-channel-context')"),
        "echo-message should reuse channel-context target classification");
    assertTransportIndependentFocusedBuild(chatHistoryBuild, false);
    assertTransportIndependentFocusedBuild(replyBuild, true);
    assertTransportIndependentFocusedBuild(reactionsBuild, true);
    assertMessageTagSignalBuild(replyBuild);
    assertMessageTagSignalBuild(reactionsBuild);
    assertMessageTagSignalBuild(channelContextBuild);
    assertTransportIndependentFocusedBuild(typingBuild, true);
    assertMessageTagSignalBuild(typingBuild);
    assertTransportIndependentFocusedBuild(readMarkerBuild, true);
    assertMessageTagSignalBuild(readMarkerBuild);
    assertTransportIndependentFocusedBuild(redactionBuild, true);
    assertMessageTagSignalBuild(redactionBuild);
    assertTransportIndependentFocusedBuild(messageEditBuild, true);
    assertMessageTagSignalBuild(messageEditBuild);
    assertTransportIndependentFocusedBuild(saslBuild, false);
    assertTransportIndependentFocusedBuild(stsBuild, false);
    for (Path focusedPresenceBuild :
        List.of(
            awayNotifyBuild,
            accountNotifyBuild,
            extendedJoinBuild,
            chghostBuild,
            setnameBuild,
            inviteNotifyBuild)) {
      assertTransportIndependentFocusedBuild(focusedPresenceBuild, false);
    }
    assertTransportIndependentFocusedBuild(monitorBuild, false);
    assertTransportIndependentFocusedBuild(standardRepliesBuild, false);
    assertTransportIndependentFocusedBuild(accountTagBuild, false);
    assertTransportIndependentFocusedBuild(userIdentityBuild, false);
    assertTransportIndependentFocusedBuild(batchBuild, false);
    assertTransportIndependentFocusedBuild(zncPlaybackBuild, false);

    String multilineSource = Files.readString(multilineBuild);
    assertTrue(
        multilineSource.contains("api project(':ircafe-feature-ircv3-common')"),
        "the multiline feature should consume shared CAP values through common");
    assertTrue(
        !multilineSource.contains("project(':ircafe-feature-ircv3')"),
        "the multiline feature must not depend on the removed compatibility umbrella");
    assertTrue(
        !multilineSource.contains("org.pircbotx"),
        "the multiline feature build should remain transport-independent");
  }

  private static void assertMessageTagSignalBuild(Path buildFile) throws IOException {
    String source = Files.readString(buildFile);
    assertTrue(
        source.contains("implementation project(':ircafe-feature-ircv3-message-tags')"),
        buildFile + " should consume shared tag decoding through message-tags");
    assertTrue(
        !source.contains("org.pircbotx"), buildFile + " should remain transport-independent");
  }

  private static void assertTransportIndependentFocusedBuild(Path buildFile, boolean requiresCommon)
      throws IOException {
    String source = Files.readString(buildFile);
    assertTrue(
        !source.contains("project(':ircafe-feature-ircv3')"),
        buildFile + " must not depend on the removed compatibility umbrella");
    assertTrue(
        !source.contains("org.pircbotx"), buildFile + " should remain transport-independent");
    if (requiresCommon) {
      assertTrue(
          source.contains("implementation project(':ircafe-feature-ircv3-common')"),
          buildFile + " should consume shared IRCv3 command policy through common");
    }
  }

  @Test
  void ircv3CycloneDxTasksUseSharedTransitiveFeatureJarConvention() throws IOException {
    Path convention = Path.of("gradle/java-library-subproject-conventions.gradle");
    String conventionSource = Files.readString(convention);

    assertTrue(
        conventionSource.contains("collectProjectDependencies")
            && conventionSource.contains("withType(org.gradle.api.artifacts.ProjectDependency)")
            && conventionSource.contains("source.project(dependency.path)")
            && conventionSource.contains(
                "collectProjectDependencies(dependencyProject, discovered)")
            && conventionSource.contains("cyclonedxDirectBom")
            && conventionSource.contains("dependsOn(provider")
            && conventionSource.contains("it.tasks.named('jar')"),
        "the shared Java-library convention should make CycloneDX depend recursively on project "
            + "JAR producers");

    assertUsesIrcv3FeatureConvention(Path.of("ircafe-feature-ircv3-channel-context/build.gradle"));
    assertUsesIrcv3FeatureConvention(Path.of("ircafe-feature-ircv3-server-time/build.gradle"));
    assertUsesIrcv3FeatureConvention(Path.of("ircafe-feature-ircv3-echo-message/build.gradle"));
  }

  private static void assertUsesIrcv3FeatureConvention(Path buildFile) throws IOException {
    String source = Files.readString(buildFile);
    assertTrue(
        source.contains("gradle/ircv3-feature-conventions.gradle"),
        buildFile
            + " should inherit transitive CycloneDX producer wiring from the shared convention");
  }

  @Test
  void javaFormattingCoverageIncludesEveryRegisteredSubprojectSourceTree() throws IOException {
    String quality = Files.readString(Path.of("gradle/quality.gradle"));
    assertTrue(
        quality.contains("'src/**/*.java'")
            && quality.contains("'ircafe-*/src/**/*.java'")
            && quality.contains("tasks.register('verifyJavaFormattingCoverage')")
            && quality.contains("rootProject.allprojects.collectMany")
            && quality.contains("candidate.fileTree('src')")
            && quality.contains("include '**/*.java'")
            && quality.contains("include(javaFormattingTargets)"),
        "formatting coverage should include root and every registered Java subproject "
            + "without enumerating modules");
  }

  @Test
  void pullRequestFormattingAutofixDoesNotRunErrorPronePatches() throws IOException {
    String quality = Files.readString(Path.of("gradle/quality.gradle"));
    String workflow = Files.readString(Path.of(".github/workflows/pr-spotless-autofix.yml"));

    int taskStart = quality.indexOf("tasks.register('applyFormatting')");
    int taskEnd = quality.indexOf("tasks.named('spotlessCheck')", taskStart);
    assertTrue(taskStart >= 0 && taskEnd > taskStart, "the formatting-only task should exist");

    String taskSource = quality.substring(taskStart, taskEnd);
    assertTrue(
        containsIgnoringWhitespace(
                taskSource, "dependsOn('spotlessJavaApply', 'spotlessMiscApply')")
            && !taskSource.contains("errorProneApply")
            && !taskSource.contains("spotlessApply"),
        "the formatting-only lifecycle task should use only focused Spotless targets");
    assertTrue(
        workflow.contains("run: ./gradlew --no-daemon applyFormatting")
            && !workflow.contains("run: ./gradlew --no-daemon spotlessApply")
            && !workflow.contains("errorProneApply"),
        "the PR autofix workflow should not run aggregate Spotless or Error Prone patch tasks");
  }

  @Test
  void ircv3MigrationCheckUsesRegisteredProjectsWithoutFormattingTasks() throws IOException {
    String verification = Files.readString(Path.of("gradle/plugin-release-verification.gradle"));
    assertTrue(
        verification.contains("tasks.register('ircv3MigrationCheck')")
            && verification.contains("it.name.startsWith('ircafe-feature-ircv3-')")
            && verification.contains("it.name.startsWith('ircafe-builtins-ircv3-')")
            && verification.contains("it.tasks.named('test')")
            && verification.contains("it.tasks.named('cyclonedxDirectBom')")
            && verification.contains("tasks.named('architectureTest')")
            && verification.contains("tasks.named('verifyBuiltInProviderPackaging')")
            && verification.contains("tasks.named('verifyBootJarPluginPackaging')")
            && verification.contains("tasks.named('verifyJavaFormattingCoverage')"),
        "IRCv3 verification should discover registered projects and cover tests, boundaries, "
            + "BOMs, packaging, and formatting coverage");

    int taskStart = verification.indexOf("tasks.register('ircv3MigrationCheck')");
    int taskEnd = verification.indexOf("tasks.register('externalPluginSmokeTest'", taskStart);
    String taskSource = verification.substring(taskStart, taskEnd);
    assertTrue(
        !taskSource.contains("spotlessCheck") && !taskSource.contains("spotlessApply"),
        "the authoritative IRCv3 migration check must not run formatting tasks");
  }

  private static Set<Path> featureProjectDirs() throws IOException {
    Set<Path> projectDirs = new TreeSet<>();
    for (String projectName : featureProjectNames()) {
      Path projectDir = Path.of(projectName);
      assertTrue(
          Files.isDirectory(projectDir),
          "settings.gradle includes " + projectName + " but its project directory is missing");
      projectDirs.add(projectDir);
    }
    return projectDirs;
  }

  private static Set<String> featureProjectNames() throws IOException {
    Set<String> projectNames = new TreeSet<>();
    Matcher matcher = FEATURE_INCLUDE_PATTERN.matcher(Files.readString(Path.of("settings.gradle")));
    while (matcher.find()) {
      projectNames.add(matcher.group(1));
    }
    assertTrue(!projectNames.isEmpty(), "settings.gradle should declare feature projects");
    return projectNames;
  }
}
