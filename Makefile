.DEFAULT_GOAL := help

GRADLEW ?= ./gradlew
LOCAL_GRADLE_USER_HOME ?= $(CURDIR)/.gradle-local
GRADLE_FLAGS ?=
TASKS ?= tasks --all

DOCKER ?= docker
DOCKER_IMAGE ?= ircafe-build:temurin-25
DOCKER_BUILD_FILE ?= Dockerfile.build
DOCKER_WORKDIR ?= /workspace
DOCKER_CACHE_DIR ?= $(CURDIR)/.gradle-docker
DOCKER_GRADLE_USER_HOME ?= /tmp/gradle-user-home
DOCKER_GRADLE_FLAGS ?= --no-daemon
DOCKER_UID ?= $(shell id -u 2>/dev/null || echo 1000)
DOCKER_GID ?= $(shell id -g 2>/dev/null || echo 1000)
DOCKER_TTY ?= $(shell if [ -t 1 ]; then printf -- "-t"; fi)

DOCKER_RUN = $(DOCKER) run --rm $(DOCKER_TTY) \
	-u $(DOCKER_UID):$(DOCKER_GID) \
	-v "$(CURDIR):$(DOCKER_WORKDIR)" \
	-v "$(DOCKER_CACHE_DIR):$(DOCKER_GRADLE_USER_HOME)" \
	-e GRADLE_USER_HOME="$(DOCKER_GRADLE_USER_HOME)" \
	-w "$(DOCKER_WORKDIR)" \
	$(DOCKER_IMAGE)

GRADLE_RUN = GRADLE_USER_HOME="$(LOCAL_GRADLE_USER_HOME)" $(GRADLEW)

.PHONY: help gradle bootrun build jar check lint test integration-test architecture-test functional-test clean jpackage \
	docker-image docker-image-if-missing \
	docker-gradle docker-build docker-check docker-test docker-lint docker-clean docker-jar \
	docker-integration-test docker-architecture-test docker-functional-test

help: ## Show available make targets
	@awk 'BEGIN {FS = ":.*## "}; /^[a-zA-Z0-9_.-]+:.*## / {printf "%-20s %s\n", $$1, $$2}' $(MAKEFILE_LIST)

gradle: ## Run arbitrary local Gradle tasks, e.g. make gradle TASKS="bootRun"
	$(GRADLE_RUN) $(TASKS) $(GRADLE_FLAGS)

bootrun: ## Launch IRCafe from source (local JDK required)
	$(GRADLE_RUN) bootRun $(GRADLE_FLAGS)

build: ## Build binaries and run verification tasks
	$(GRADLE_RUN) build $(GRADLE_FLAGS)

jar: ## Build runnable boot jar under build/libs
	$(GRADLE_RUN) bootJar $(GRADLE_FLAGS)

check: ## Run lint + tests + configured checks
	$(GRADLE_RUN) check $(GRADLE_FLAGS)

lint: ## Run static analysis and style checks
	$(GRADLE_RUN) lint $(GRADLE_FLAGS)

test: ## Run unit/non-functional tests (excludes FunctionalTest)
	$(GRADLE_RUN) test $(GRADLE_FLAGS)

integration-test: ## Run IntegrationTest suite from src/test
	$(GRADLE_RUN) integrationTest $(GRADLE_FLAGS)

architecture-test: ## Run module boundary/architecture guardrail tests
	$(GRADLE_RUN) architectureTest $(GRADLE_FLAGS)

functional-test: ## Run Swing FunctionalTest suite from src/functionalTest
	$(GRADLE_RUN) functionalTest $(GRADLE_FLAGS)

clean: ## Clean local build outputs
	$(GRADLE_RUN) clean $(GRADLE_FLAGS)

jpackage: ## Build app image with jpackage (local toolchain requirements apply)
	$(GRADLE_RUN) jpackage $(GRADLE_FLAGS)

docker-image: ## Build the pinned Docker builder image used by docker-* targets
	$(DOCKER) build -f "$(DOCKER_BUILD_FILE)" -t "$(DOCKER_IMAGE)" .

docker-image-if-missing:
	@$(DOCKER) image inspect "$(DOCKER_IMAGE)" >/dev/null 2>&1 || $(MAKE) docker-image

docker-gradle: docker-image-if-missing ## Run arbitrary Gradle tasks in Docker, e.g. make docker-gradle TASKS="build"
	@mkdir -p "$(DOCKER_CACHE_DIR)"
	$(DOCKER_RUN) ./gradlew $(DOCKER_GRADLE_FLAGS) $(TASKS) $(GRADLE_FLAGS)

docker-build: ## Build in Docker with JDK 25 (no local JDK needed)
	$(MAKE) docker-gradle TASKS="build"

docker-check: ## Run full check in Docker (lint + tests + checks)
	$(MAKE) docker-gradle TASKS="check"

docker-test: ## Run test task in Docker
	$(MAKE) docker-gradle TASKS="test"

docker-integration-test: ## Run integrationTest task in Docker
	$(MAKE) docker-gradle TASKS="integrationTest"

docker-architecture-test: ## Run architectureTest task in Docker
	$(MAKE) docker-gradle TASKS="architectureTest"

docker-functional-test: ## Run functionalTest task in Docker
	$(MAKE) docker-gradle TASKS="functionalTest"

docker-lint: ## Run lint task in Docker
	$(MAKE) docker-gradle TASKS="lint"

docker-clean: ## Clean build outputs in Docker
	$(MAKE) docker-gradle TASKS="clean"

docker-jar: ## Build runnable boot jar in Docker
	$(MAKE) docker-gradle TASKS="bootJar"
