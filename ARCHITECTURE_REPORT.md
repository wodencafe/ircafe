# Repeatable architecture review

Run `make architecture-report` (or `GRADLE_USER_HOME=.gradle-local ./gradlew architectureReport`).
Read `build/reports/architecture/architecture.md`; use the accompanying `architecture.json`
for scripts, issue triage, or comparisons. No model, network service, running application, or
Spring context is needed for the analysis. The first Gradle build may need dependency downloads.

The task compiles current sources and inspects production classes from every Java project,
including feature, plugin API, and built-in provider artifacts. It reuses the existing ArchUnit,
jMolecules, Spring Modulith, and Jackson dependencies. Its implementation lives with the
architecture test tooling and is excluded from application artifacts. Reports are opt-in;
advisory findings do not fail the build. Analysis errors do fail the task.

## Findings

| Rule | Meaning | Suggested review |
| --- | --- | --- |
| `MODULITH_VIOLATION` | Actual `ApplicationModules.detectViolations()` output, including module cycles and prohibited dependencies | Fix module contracts or dependency direction |
| `ARCHUNIT_VIOLATION` | Actual rules from `ArchitectureGuardrailsTest` and `JmoleculesDependencyRulesTest` | Fix the reported dependency using the project's established architecture |
| `MODULITH_OPEN_MODULE` | Discovered module exposes its internals | Identify callers and introduce a minimal named interface before closing it |
| `MODULITH_UNRESTRICTED_DEPENDENCIES` | Discovered module has no explicit dependency restrictions | Review allowed module and named-interface dependencies |
| `CROSS_MODULE_CONCRETE_COMPONENT` | A production type depends on another Modulith module's concrete Spring component | Consider an existing port or a smaller public contract |
| `JMOLECULES_PORT_ROLE` | Interface named `*Port` or in a `port` package lacks an effective port role | Decide its role, then add an annotation and dependency rule if appropriate |
| `SPI_IMPLEMENTATION_COUPLING` | One artifact depends on another artifact's class registered in `META-INF/services` | Depend on the service contract and existing plugin loader |
| `SPI_PROVIDER_CONTRACT` | A local classpath provider is not assignable to its local service contract | Repair provider or descriptor |
| `SPI_PROVIDER_UNRESOLVED` | Descriptor names a provider absent from the production project outputs | Check packaging; the provider may intentionally come from an external dependency |
| `CLASS_FAN_OUT` | Top-level class has at least 25 distinct dependencies on other production types | Review cohesive responsibility extraction |
| `LARGE_COMPONENT_CONSTRUCTOR` | Spring component's largest constructor has at least 10 parameters | Review coordination responsibilities |

P1 findings are rule violations, P2 findings concern boundaries, and P3 findings are structural
heuristics. Within a priority, findings sort by descending distinct evidence count, then stable
ID. This is a triage order, not a prediction of refactoring benefit. Each entry includes evidence,
a source path where available, and a suggested review. Bytecode dependency descriptions include
source lines where debug information allows it. Evidence samples are sorted and capped at 20
per finding; JSON retains the total count. Findings themselves are not truncated.

The module model comes from Spring Modulith rather than directory-name guesses. Artifact
ownership comes from each project's compiled outputs, so shared package names do not hide
cross-artifact dependencies. Duplicate class ownership fails the scan. Port-role detection
accounts for direct annotations, meta-annotations, and ancestor package annotations.

## Repeat runs and comparison

```sh
make architecture-report
cp build/reports/architecture/architecture.json /tmp/ircafe-architecture-before.json
# Make a focused change, then compare:
make architecture-report GRADLE_FLAGS='-ParchitectureBaseline=/tmp/ircafe-architecture-before.json'
```

The JSON `delta` lists added and resolved finding IDs. IDs use rule and subject, so moving a
source line does not create a new finding. Evidence changes under an existing ID are not marked
as new findings. Preserve a baseline outside the report directory; reports are replaced on each
run. Compare reports produced with the same thresholds and tooling version for useful results.
The schema is versioned; invalid baselines fail visibly. Baselines never suppress findings.

Tune thresholds without changing code:

```sh
make architecture-report GRADLE_FLAGS='-ParchitectureFanOut=35 -ParchitectureConstructorSize=12'
```

Gradle tracks project ownership/layout, compiled outputs, source/resources, report implementation, thresholds, and the
optional baseline as inputs. It can reuse the report when those inputs are unchanged.
The report uses a copy of application resources without Spring Boot's changing
`META-INF/build-info.properties`; application packaging keeps its normal build metadata.

## Interpretation and limits

Candidates require design judgment. An exported concrete service can be a perfectly adequate
API; a large constructor may belong to a composition root. Type and annotation dependencies
contribute to fan-out. The scan does not infer aggregate roots, business invariants, or where
asynchronous domain events would preserve behavior. It does not instantiate providers, inspect
reflective string-based calls, or prove runtime Spring wiring, EDT safety, or lifecycle cleanup.
SPI descriptor checks cover source resource directories and classpath services, not JPMS
`provides` directives or descriptors generated by packaging tasks.
The fan-out, constructor, port-role, and concrete-component heuristics analyze top-level types;
nested classes still participate in the existing ArchUnit, Modulith, and SPI checks.

Keep `./gradlew architectureTest` as the complete enforcement suite. The report evaluates the
two named ArchUnit rule holders and Modulith's verification, not every JUnit architecture test.
In particular it deliberately retains this project's dependency-inverted jMolecules rules
instead of enabling the stock layered rules. Tests for the analyzer compile isolated fixtures
outside the scanned classpath, so deliberate violations cannot pollute application discovery.

After choosing a candidate, implement the smallest cohesive change and run the relevant tests.
An accepted boundary should become an ArchUnit or Modulith guardrail to prevent regression.
See [Spring Modulith verification](https://docs.spring.io/spring-modulith/reference/verification.html)
for the model's verification semantics.
