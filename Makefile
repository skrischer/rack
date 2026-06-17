# Rack — root developer commands.
# See docs/workflow.md (Commands) for the bootstrap/verify/build contract.

.PHONY: help bootstrap verify build

help:
	@echo "Rack — available targets:"
	@echo "  make bootstrap  Install MCP deps and create local .env / android/local.properties from templates (only when absent)"
	@echo "  make verify     MCP verify (lint + typecheck + test) then Android ktlint + detekt"
	@echo "  make build      MCP build then Android assembleDebug"

bootstrap:
	cd mcp && npm ci
	[ -f .env ] || cp .env.example .env
	[ -f android/local.properties ] || cp android/local.properties.template android/local.properties

verify:
	cd mcp && npm run verify
	cd android && ./gradlew ktlintCheck detekt

build:
	cd mcp && npm run build
	cd android && ./gradlew assembleDebug
