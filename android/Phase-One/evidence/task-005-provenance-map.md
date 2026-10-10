# TASK-005 per-file provenance map

## Location retry additions (t_9fd2d126)

| :app | `android/app/src/main/kotlin/ai/eqo/task/TaskPlanRetry.kt` | EQO-NEW | `ai.eqo.task` | Process-only completed-step memory; no replay of unknown effects |
| :app | `android/app/src/test/kotlin/ai/eqo/task/TaskPlanRetryTest.kt` | EQO-NEW | `ai.eqo.task` | Fake controller/executor retries without duplicate sends |


## Voice v2 additions (t_e9801e01)

| :app | `android/app/src/main/kotlin/ai/eqo/task/PhoneVoiceInput.kt` | EQO-NEW | `ai.eqo.task` | Pause-tolerant phone recognizer session and stale callback guards |
| :app | `android/app/src/main/kotlin/ai/eqo/task/AiVoiceInput.kt` | EQO-NEW | `ai.eqo.task` | Opt-in AI recording/transcription session and cancellation |

| :app | `android/app/src/main/kotlin/ai/eqo/task/VoiceSettings.kt` | EQO-NEW | `ai.eqo.task` | Non-secret engine/language/consent preferences and setup controls |
| :app | `android/app/src/main/kotlin/ai/eqo/task/VoiceAudioRecorder.kt` | EQO-NEW | `ai.eqo.task` | Bounded ephemeral PCM/WAV capture and file cleanup |
| :app | `android/app/src/test/kotlin/ai/eqo/task/VoiceInputV2Test.kt` | EQO-NEW | `ai.eqo.task` | Fake recorder/provider draft-only consent and cleanup regressions |
| :core-llm | `android/core-llm/src/test/java/ai/eqo/core/llm/providers/OpenRouterAudioTest.kt` | EQO-NEW | `ai.eqo.core.llm.providers` | Synthetic input_audio and capability tests; no network |
## Explain/handle UX polish (t_e84b3eaa)

| :app | `android/app/src/main/kotlin/ai/eqo/explain/ExplainPanelState.kt` | EQO-NEW | `ai.eqo.explain` | Bounded panel sizes and clock-testable five-second touch-through state |
| :app | `android/app/src/test/kotlin/ai/eqo/explain/ExplainPolishTest.kt` | EQO-NEW | `ai.eqo.explain` | Panel, reopen, notification refresh, entry routes, hub and hidden-app regressions |
## Pairing discovery fix (t_9a512691)

| :app | `android/app/src/main/kotlin/ai/eqo/onboarding/MdnsMessage.kt` | EQO-NEW | `ai.eqo.onboarding` | Bounded DNS SRV/A/AAAA parser, compression loop guard and own-address validation |
| :app | `android/app/src/main/kotlin/ai/eqo/onboarding/WifiMdnsResolver.kt` | EQO-NEW | `ai.eqo.onboarding` | Wi-Fi-bound multicast query with bounded retry and one-winner framework fallback |
| :app | `android/app/src/main/kotlin/ai/eqo/onboarding/WirelessPairingReply.kt` | EQO-NEW | `ai.eqo.onboarding` | Strict transient code and optional explicit ports; revision-bound wait when connect port is unknown |
| :app | `android/app/src/debug/kotlin/ai/eqo/onboarding/DebugPairReceiver.kt` | EQO-NEW | `ai.eqo.onboarding` | Debug source-set lab receiver, DEBUG guard, DUMP-protected manifest and shared pairing service |
| :app | `android/app/src/test/kotlin/ai/eqo/onboarding/MdnsMessageTest.kt` | EQO-NEW | `ai.eqo.onboarding` | Synthetic DNS packets, compression, truncation, bounds and own-host safety |
| :app | `android/app/src/test/kotlin/ai/eqo/onboarding/WirelessResolveAttemptTest.kt` | EQO-NEW | `ai.eqo.onboarding` | Fake framework success/failure/timeout, fallback order and late callbacks |
| :app | `android/app/src/test/kotlin/ai/eqo/onboarding/WirelessPairingReplyTest.kt` | EQO-NEW | `ai.eqo.onboarding` | Reply validation and fake pair/connect runner without helper authorization |
| :app | `android/app/src/testDebug/kotlin/ai/eqo/onboarding/DebugPairReceiverTest.kt` | EQO-NEW | `ai.eqo.onboarding` | Debug receiver forwarding, malformed extras and release-source-set exclusion guard |

## Edge-handle additions (t_a3fa16d0)

| :platform-a11y | `android/platform-a11y/src/main/java/ai/eqo/accessibility/handle/HandleShortcutRegistry.kt` | EQO-NEW | `ai.eqo.accessibility.handle` | Internal shortcut API and fake-store-testable choice/order |
| :platform-a11y | `android/platform-a11y/src/main/java/ai/eqo/accessibility/handle/HandlePreferences.kt` | EQO-NEW | `ai.eqo.accessibility.handle` | App-private choices, placement and hidden apps |
| :platform-a11y | `android/platform-a11y/src/main/java/ai/eqo/accessibility/handle/HandleWindowGuard.kt` | EQO-NEW | `ai.eqo.accessibility.handle` | Overlay coordinate and own-window guard |
| :platform-a11y | `android/platform-a11y/src/main/java/ai/eqo/accessibility/handle/EdgeHandleOverlay.kt` | EQO-NEW | `ai.eqo.accessibility.handle` | User-only accessibility handle and dynamic panel |
| :platform-a11y | `android/platform-a11y/src/test/java/ai/eqo/accessibility/handle/HandleShortcutRegistryTest.kt` | EQO-NEW | `ai.eqo.accessibility.handle` | Fake registry/order/default/availability regressions |
| :platform-a11y | `android/platform-a11y/src/test/java/ai/eqo/accessibility/HandleWindowSafetyTest.kt` | EQO-NEW | `ai.eqo.accessibility` | Underlying-window gesture and takeover exclusions |
| :app | `android/app/src/main/kotlin/ai/eqo/handle/BuiltInHandleShortcuts.kt` | EQO-NEW | `ai.eqo.handle` | Existing controller Pause/Stop and app/task entries |
| :app | `android/app/src/main/kotlin/ai/eqo/handle/EdgeHandleSettingsActivity.kt` | EQO-NEW | `ai.eqo.handle` | Opt-in, shortcut choice, Up/Down, reset and restore |
| :app | `android/app/src/test/kotlin/ai/eqo/handle/HandleFeaturesTest.kt` | EQO-NEW | `ai.eqo.handle` | Preference, focus and real public controller regressions |
## Explain screen additions (t_699c0abc)

| :app | `android/app/src/main/kotlin/ai/eqo/explain/ExplainSession.kt` | EQO-NEW | `ai.eqo.explain` | Read-only explanation session, privacy bounds and protected-screen policy seam |
| :app | `android/app/src/main/kotlin/ai/eqo/explain/ExplainRuntime.kt` | EQO-NEW | `ai.eqo.explain` | Foreground tree extraction, memory-only capture and configured provider adapter |
| :app | `android/app/src/main/kotlin/ai/eqo/explain/ExplainEntry.kt` | EQO-NEW | `ai.eqo.explain` | Tile, transient entry, notification shortcut and Boolean preferences |
| :app | `android/app/src/main/kotlin/ai/eqo/explain/ExplainOverlay.kt` | EQO-NEW | `ai.eqo.explain` | Accessibility result sheet, typed follow-ups and device speech |
| :app | `android/app/src/main/kotlin/ai/eqo/explain/ExplainSettingsActivity.kt` | EQO-NEW | `ai.eqo.explain` | Screen-sharing consent, auto-read and notification settings |
| :app | `android/app/src/test/kotlin/ai/eqo/explain/ExplainSessionTest.kt` | EQO-NEW | `ai.eqo.explain` | Fake-source/model decision, privacy and lifetime regressions |
| :app | `android/app/src/test/kotlin/ai/eqo/explain/ExplainAndroidTest.kt` | EQO-NEW | `ai.eqo.explain` | Android extractor, capability metadata and explicit notification routing regressions |

## Voice-input additions (t_e9ef0f95)

| :app | `android/app/src/main/kotlin/ai/eqo/task/VoiceInputPresenter.kt` | EQO-NEW | `ai.eqo.task` | Draft-only speech presentation, no submission capability |
| :app | `android/app/src/main/kotlin/ai/eqo/task/TaskVoiceInput.kt` | EQO-NEW | `ai.eqo.task` | Just-in-time microphone permission and Android speech adapter |
| :app | `android/app/src/test/kotlin/ai/eqo/task/VoiceInputPresenterTest.kt` | EQO-NEW | `ai.eqo.task` | Fake voice result, permission, error and stale callback regressions |
| :app | `android/app/src/test/kotlin/ai/eqo/task/TaskVoiceInputTest.kt` | EQO-NEW | `ai.eqo.task` | Android speech intent and error mapping regressions |
## One-step pairing additions (t_681e8ea6)

| :app | `android/app/src/main/kotlin/ai/eqo/onboarding/WirelessDiscoveryState.kt` | EQO-NEW | `ai.eqo.onboarding` | Local Wi-Fi endpoint eligibility and strict notification code parsing |
| :app | `android/app/src/main/kotlin/ai/eqo/onboarding/WirelessAdbDiscovery.kt` | EQO-NEW | `ai.eqo.onboarding` | NsdManager local-phone discovery and timeout fallback |
| :app | `android/app/src/main/kotlin/ai/eqo/onboarding/WirelessPairingService.kt` | EQO-NEW | `ai.eqo.onboarding` | Bounded foreground discovery and user notification reply; no helper consent |
| :app | `android/app/src/test/kotlin/ai/eqo/onboarding/WirelessDiscoveryStateTest.kt` | EQO-NEW | `ai.eqo.onboarding` | Synthetic discovery and notification code regressions |
| :app | `android/app/src/test/kotlin/ai/eqo/onboarding/WirelessPairingNotificationTest.kt` | EQO-NEW | `ai.eqo.onboarding` | Android RemoteInput envelope consumption and explicit notification routing regressions |

## Missing-app preflight additions

| :actions-android | `android/actions-android/src/main/java/ai/eqo/actions/impl/LaunchableAppResolver.kt` | EQO-NEW | `ai.eqo.actions.impl` | Local shared launchable-app resolution |
| :app | `android/app/src/main/kotlin/ai/eqo/task/MissingAppFallback.kt` | EQO-NEW | `ai.eqo.task` | Pre-approval Chrome fallback and explicit store search |
| :app | `android/app/src/test/kotlin/ai/eqo/task/MissingAppFallbackTest.kt` | EQO-NEW | `ai.eqo.task` | Local fake preflight and approval regressions |

## Run-status additions (t_4fa4cbd6)

| :app | `android/app/src/main/kotlin/ai/eqo/task/RunStatusMapping.kt` | EQO-NEW | `ai.eqo.task` | Presentation-only handoff and plain failure mapping |
| :app | `android/app/src/test/kotlin/ai/eqo/task/RunStatusMappingTest.kt` | EQO-NEW | `ai.eqo.task` | Handoff, diagnosis and non-secret logging regressions |

## WhatsApp call additions (t_d3c58c41)

| :actions-android | `android/actions-android/src/main/java/ai/eqo/actions/impl/WhatsAppCallFlow.kt` | EQO-NEW | `ai.eqo.actions.impl` | EQO targeted WhatsApp chat deep link and single-attempt gated call |
| :actions-android | `android/actions-android/src/test/java/ai/eqo/actions/impl/WhatsAppCallTest.kt` | EQO-NEW | `ai.eqo.actions.impl` | EQO registry and fake-tree call regressions; no real calls |

## TASK-077 additions (issue #20)

| :app | `android/app/src/main/kotlin/ai/eqo/task/TaskRunService.kt` | EQO-NEW | `ai.eqo.task` | EQO foreground run owner and UI snapshot |
| :app | `android/app/src/main/kotlin/ai/eqo/task/DebugPlanReceiver.kt` | EQO-NEW | `ai.eqo.task` | EQO foreground run owner and UI snapshot |
| :app | `android/app/src/main/kotlin/ai/eqo/task/PlanApprovalSettings.kt` | EQO-NEW | `ai.eqo.task` | EQO plan approval preference |
| :app | `android/app/src/test/kotlin/ai/eqo/task/ForegroundPlanRunTest.kt` | EQO-NEW | `ai.eqo.task` | EQO lifecycle, executor and preference tests |
| :core-agent | `android/core-agent/src/main/java/ai/eqo/core/agent/RegistryPlanVocabulary.kt` | EQO-NEW | `ai.eqo.core.agent` | EQO registry planner contract |
| :core-agent | `android/core-agent/src/test/java/ai/eqo/core/agent/RegistryPlannerTest.kt` | EQO-NEW | `ai.eqo.core.agent` | EQO parser/repair/schema tests |
| :platform-a11y | `android/platform-a11y/src/test/java/ai/eqo/accessibility/EqoTypingFallbackTest.kt` | EQO-NEW | `ai.eqo.accessibility` | EQO native field/fallback fake-tree tests |
| :platform-a11y | `android/platform-a11y/src/test/java/ai/eqo/accessibility/NavigationRetryTest.kt` | EQO-NEW | `ai.eqo.accessibility` | EQO bounded navigation tests |


Covers every Kotlin file in `android/` (`git ls-files android/` .kt = 183 files after the TASK-005 rename, the review round 1 test addition, the TASK-014 rebase additions, and the TASK-006 rebase additions), not only the 102 TASK-004 move files.
Upstream facts come from the Origin headers and `task-004-tools/move-manifest.json`
(origin commit `6ff5a061755b597b0558fed1f565587837ed4d51`); verify before editing.

Kinds: **MOVE** (133) = moved verbatim, carries `// Origin:` header (verify-move.py 102/102
byte-identical besides the header + 3 run-2 relocations); **ADAPTER** (24) = EQO-authored
minimal interface standing in for quarantined upstream code; **EQO-NEW** (26) = EQO-authored
app skeleton and guard tests (3 at TASK-005, 6 added by TASK-014 issue #19, 17 added by
TASK-006 issue #11).

## TASK-069 additions (issue #20)

| :actions-android | `android/actions-android/src/main/java/ai/eqo/actions/impl/ActionAdapters.kt` | EQO-NEW | `ai.eqo.actions.impl` | EQO TASK-069 gated facade adapters |
| :actions-android | `android/actions-android/src/main/java/ai/eqo/actions/impl/PermissionRequester.kt` | EQO-NEW | `ai.eqo.actions.impl` | EQO TASK-069 permission and telemetry seams |
| :actions-android | `android/actions-android/src/main/java/ai/eqo/actions/impl/AndroidActionRegistry.kt` | EQO-NEW | `ai.eqo.actions.impl` | EQO TASK-069 explicit registry |
| :actions-android | `android/actions-android/src/main/java/ai/eqo/actions/impl/AdvancedControlActions.kt` | ADAPTER | `ai.eqo.actions.impl` | yashab-cyber/opendroid @ 6ff5a061, actions/AdvancedControlActions.kt selected families |
| :actions-android | `android/actions-android/src/main/java/ai/eqo/actions/impl/CommunicationActions.kt` | ADAPTER | `ai.eqo.actions.impl` | yashab-cyber/opendroid @ 6ff5a061, actions/CommunicationActions.kt |
| :actions-android | `android/actions-android/src/main/java/ai/eqo/actions/impl/CallFlowExecutor.kt` | ADAPTER | `ai.eqo.actions.impl` | yashab-cyber/opendroid @ 6ff5a061, actions/CallFlowExecutor.kt |
| :actions-android | `android/actions-android/src/main/java/ai/eqo/actions/impl/CallFlowVerifier.kt` | ADAPTER | `ai.eqo.actions.impl` | yashab-cyber/opendroid @ 6ff5a061, accessibility/CallFlowVerifier.kt |
| :actions-android | `android/actions-android/src/main/java/ai/eqo/actions/impl/StorageWorkspaceProvider.kt` | ADAPTER | `ai.eqo.actions.impl` | yashab-cyber/opendroid @ 6ff5a061, core/storage/StorageWorkspaceProvider.kt |
| :actions-android | `android/actions-android/src/main/java/ai/eqo/actions/impl/SaveSensitiveInfoAction.kt` | ADAPTER | `ai.eqo.actions.impl` | yashab-cyber/opendroid @ 6ff5a061, actions/CalendarActions.kt 582-602 |
| :actions-android | `android/actions-android/src/test/java/ai/eqo/actions/impl/AndroidActionRegistryTest.kt` | EQO-NEW | `ai.eqo.actions.impl` | EQO TASK-069 regressions |
| :actions-android | `android/actions-android/src/main/java/ai/eqo/actions/impl/RegistryExecution.kt` | EQO-NEW | `ai.eqo.actions.impl` | EQO TASK-069 coroutine-scoped registry permit |
| :actions-android | `android/actions-android/src/test/java/ai/eqo/actions/impl/CallFamilyTest.kt` | EQO-NEW | `ai.eqo.actions.impl` | EQO TASK-069 call regressions |
| :app | `android/app/src/test/kotlin/ai/eqo/task/TaskPermissionRequesterTest.kt` | EQO-NEW | `ai.eqo.task` | EQO TASK-069 permission UI regressions |
| :app | `android/app/src/main/kotlin/ai/eqo/task/TaskPermissionRequester.kt` | EQO-NEW | `ai.eqo.task` | EQO TASK-069 task permission adapter |
## TASK-068 additions (issue #20)

| :app | `android/app/src/main/kotlin/ai/eqo/task/StudyAppIntents.kt` | EQO-NEW | `ai.eqo.task` | imbalki/project-eqo (EQO-authored, TASK-068 issue #20) |
| :app | `android/app/src/main/kotlin/ai/eqo/task/TaskPlanningRuntime.kt` | EQO-NEW | `ai.eqo.task` | imbalki/project-eqo (EQO-authored, TASK-068 issue #20) |
| :app | `android/app/src/test/kotlin/ai/eqo/task/NaturalTaskFlowTest.kt` | EQO-NEW | `ai.eqo.task` | imbalki/project-eqo (EQO-authored, TASK-068 issue #20) |
| :core-agent | `android/core-agent/src/main/java/ai/eqo/core/agent/TaskPlanner.kt` | EQO-NEW | `ai.eqo.core.agent` | imbalki/project-eqo (EQO-authored, TASK-068 issue #20) |
| :core-agent | `android/core-agent/src/test/java/ai/eqo/core/agent/TaskPlannerTest.kt` | EQO-NEW | `ai.eqo.core.agent` | imbalki/project-eqo (EQO-authored, TASK-068 issue #20) |
| :core-agent | `android/core-agent/src/main/java/ai/eqo/core/agent/TaskDisplayText.kt` | EQO-NEW | `ai.eqo.core.agent` | imbalki/project-eqo (EQO-authored, TASK-068 issue #20) |
| :core-agent | `android/core-agent/src/test/java/ai/eqo/core/agent/TaskDisplayTextTest.kt` | EQO-NEW | `ai.eqo.core.agent` | imbalki/project-eqo (EQO-authored, TASK-068 issue #20) |
| :app | `android/app/src/test/kotlin/ai/eqo/task/StudyDispatchTest.kt` | EQO-NEW | `ai.eqo.task` | imbalki/project-eqo (EQO-authored, TASK-068 issue #20) |
## TASK-073 additions (device sample follow-up)

| :app | `android/app/src/main/kotlin/ai/eqo/task/RunDiagnostics.kt` | EQO-NEW | `ai.eqo.task` | imbalki/project-eqo (EQO-authored, TASK-073) |

## TASK-070 additions (issue #20)

| :app | `android/app/src/main/kotlin/ai/eqo/onboarding/ModelPicker.kt` | EQO-NEW | `ai.eqo.onboarding` | imbalki/project-eqo (EQO-authored, TASK-070 issue #20) |
| :app | `android/app/src/test/kotlin/ai/eqo/onboarding/ModelPickerTest.kt` | EQO-NEW | `ai.eqo.onboarding` | imbalki/project-eqo (EQO-authored, TASK-070 issue #20) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/core/llm/providers/OpenRouterModelCatalog.kt` | EQO-NEW | `ai.eqo.core.llm.providers` | imbalki/project-eqo (EQO-authored, TASK-070 issue #20) |
| :core-llm | `android/core-llm/src/test/java/ai/eqo/core/llm/providers/OpenRouterModelCatalogTest.kt` | EQO-NEW | `ai.eqo.core.llm.providers` | imbalki/project-eqo (EQO-authored, TASK-070 issue #20) |

## TASK-066 additions (issue #20)

| :app | `android/app/src/main/kotlin/ai/eqo/task/SamplePractice.kt` | EQO-NEW | `ai.eqo.task` | imbalki/project-eqo (EQO-authored, TASK-066 issue #20) |
| :app | `android/app/src/main/kotlin/ai/eqo/task/TaskControlFeedback.kt` | EQO-NEW | `ai.eqo.task` | imbalki/project-eqo (EQO-authored, TASK-066 issue #20) |
| :app | `android/app/src/test/kotlin/ai/eqo/task/SmsDraftOpenerTest.kt` | EQO-NEW | `ai.eqo.task` | imbalki/project-eqo (EQO-authored, TASK-066 issue #20) |
| :app | `android/app/src/test/kotlin/ai/eqo/task/SamplePracticeTest.kt` | EQO-NEW | `ai.eqo.task` | imbalki/project-eqo (EQO-authored, TASK-066 issue #20) |
| :app | `android/app/src/test/kotlin/ai/eqo/task/SampleRunRegressionTest.kt` | EQO-NEW | `ai.eqo.task` | imbalki/project-eqo (EQO-authored, TASK-066b issue #20) |
| :app | `android/app/src/test/kotlin/ai/eqo/task/TaskControlPresentationTest.kt` | EQO-NEW | `ai.eqo.task` | imbalki/project-eqo (EQO-authored, TASK-066 issue #20) |
| :core-agent | `android/core-agent/src/test/java/ai/eqo/core/agent/ActionLoopPracticeSafetyTest.kt` | EQO-NEW | `ai.eqo.core.agent` | imbalki/project-eqo (EQO-authored, TASK-066 issue #20) |

## TASK-063 additions (issue #20)

| :app | `android/app/src/test/kotlin/ai/eqo/onboarding/SetupHubActivityTest.kt` | EQO-NEW | `ai.eqo.onboarding` | imbalki/project-eqo (EQO-authored, TASK-063 issue #20) |
| :app | `android/app/src/test/kotlin/ai/eqo/onboarding/OpenRouterProbeEndpointTest.kt` | EQO-NEW | `ai.eqo.onboarding` | imbalki/project-eqo (EQO-authored, probe-endpoint fix issue #20) |

## TASK-062 additions (issue #20)

| :app | `android/app/src/test/kotlin/ai/eqo/ProductionServiceStartupTest.kt` | EQO-NEW | `ai.eqo` | imbalki/project-eqo (EQO-authored, TASK-062 issue #20) |
| :app | `android/app/src/main/kotlin/ai/eqo/EqoApplication.kt` | EQO-NEW | `ai.eqo` | imbalki/project-eqo (EQO-authored, TASK-062 issue #20) |
| :app | `android/app/src/androidTest/kotlin/ai/eqo/RealAccessibilityServiceSmokeTest.kt` | EQO-NEW | `ai.eqo` | imbalki/project-eqo (EQO-authored, TASK-062 issue #20) |
| :platform-a11y | `android/platform-a11y/src/main/java/ai/eqo/accessibility/EqoServiceRuntime.kt` | EQO-NEW | `ai.eqo.accessibility` | imbalki/project-eqo (EQO-authored, TASK-062 issue #20) |
| :platform-a11y | `android/platform-a11y/src/test/java/ai/eqo/accessibility/EqoServiceRuntimeTest.kt` | EQO-NEW | `ai.eqo.accessibility` | imbalki/project-eqo (EQO-authored, TASK-062 issue #20) |

## TASK-014 additions (issue #19, rebase onto TASK-005)

The six TASK-014 SMS guard files (SmsComposePolicy + five guard tests) are EQO-authored,
have no upstream counterpart, and are recorded as **EQO-NEW** rows below with provenance
`imbalki/project-eqo (EQO-authored, TASK-014 issue #19)`. They were authored under the
pre-rename `com.opendroid.ai` packages and re-homed to `ai.eqo` by the TASK-014 rebase;
no content changed beyond the package rename.

## TASK-006 additions (issue #11, rebase onto TASK-005 and TASK-014)

The seventeen TASK-006 BYOK/redaction files (LogRedactor, RedactingLog,
LogRedactorCrashHook, ConnectionTestRunner, ProviderCostDisclosure, the
AllowBackupManifestTest guard and eleven further tests) are EQO-authored, have
no upstream counterpart, and are recorded as **EQO-NEW** rows below with
provenance `imbalki/project-eqo (EQO-authored, TASK-006 issue #11)`. They were
authored under the pre-rename `com.opendroid.ai` packages and re-homed to
`ai.eqo` by the TASK-006 rebase; no content changed beyond the package/path
rename (the mechanical re-base proof is in `task-006-byok-security.md`).

## Review round 1 (lead R1/R2 corrections)

`LEGACY_PREFERENCES_NAME` in KeystoreSecretStorage.kt
is restored to the upstream-era on-device file name `opendroid_prefs` (comment-only addition to
that MOVE file); the EQOAccessibilityService.kt Origin path names the real upstream file
`app/src/main/java/com/opendroid/ai/accessibility/OpenDroidAccessibilityService.kt`; the 23
ADAPTER file comments and every "upstream symbol:" entry name the true upstream
`com.opendroid.ai.*` identifiers (the fabricated `EQONotificationListener`/`EQOService` names
were corrected to upstream `OpenDroidNotificationListener`/`OpenDroidService`, verified in the
upstream tree at 6ff5a06); three rows were re-pointed at their post-relocation paths; and the
EQO-NEW test row above was added. Review round 1 verified every "upstream symbol:" entry
against the upstream tree at 6ff5a06 (the class file exists under
`app/src/*/java/com/opendroid/ai/`); scripts/check-branding.sh additionally asserts that every
map row names a path that exists in `git ls-files`.

Verification commands run (review round 1):

- `git ls-tree -r a567321 --name-only | grep OpenDroidAccessibilityService` ->
  `android/platform-a11y/src/main/java/com/opendroid/ai/accessibility/OpenDroidAccessibilityService.kt`;
  upstream clone at 6ff5a06 has `app/src/main/java/com/opendroid/ai/accessibility/OpenDroidAccessibilityService.kt`.
- Upstream symbol check (upstream clone @ 6ff5a06): for each of the 24 "upstream symbol:"
  entries, `git ls-files --error-unmatch app/src/main/java/com/opendroid/ai/<symbol path>.kt` ->
  `OK` for 24/24 (incl. `core.service.OpenDroidNotificationListener`,
  `core.service.OpenDroidService`, `core.routine.HabitRoutineEngine`, `MainActivity`).
- `git show a567321:android/core-security/src/main/java/com/opendroid/ai/core/security/KeystoreSecretStorage.kt | grep -n 'LEGACY_PREFERENCES_NAME ='` ->
  `436:        const val LEGACY_PREFERENCES_NAME = "opendroid_prefs"` (base value restored by R1).
- Gate re-run: `bash scripts/check-branding.sh` -> `kt files: 160; provenance rows: 160`, exit 0;
  stale-path probe P4 in a scratch clone -> exit 1 (see task-005-rebrand-gates.md).

Still NOT verified here: the upstream *symbols* were checked for file existence upstream, not
line-by-line semantic equivalence of the adapters (that is TASK-004's verify-move scope).

| Module | Current file (repo-relative) | Kind | Package | Upstream origin |
|---|---|---|---|---|
| :app | `android/app/src/main/kotlin/ai/eqo/MainActivity.kt` | EQO-NEW | `ai.eqo` | imbalki/project-eqo (EQO-authored) |
| :app | `android/app/src/test/kotlin/ai/eqo/AllowBackupManifestTest.kt` | EQO-NEW | `ai.eqo` | imbalki/project-eqo (EQO-authored, TASK-006 issue #11) |
| :app | `android/app/src/test/kotlin/ai/eqo/MainActivityTest.kt` | EQO-NEW | `ai.eqo` | imbalki/project-eqo (EQO-authored) |
| :app | `android/app/src/test/kotlin/ai/eqo/SmsPermissionsManifestTest.kt` | EQO-NEW | `ai.eqo` | imbalki/project-eqo (EQO-authored, TASK-014 issue #19) |
| :core-agent | `android/core-agent/src/main/java/ai/eqo/core/agent/ActionSequenceExecutor.kt` | MOVE | `ai.eqo.core.agent` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/core/agent/ActionSequenceExecutor.kt`) |
| :core-agent | `android/core-agent/src/main/java/ai/eqo/core/agent/AutoApprovalPolicy.kt` | MOVE | `ai.eqo.core.agent` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/core/agent/AutoApprovalPolicy.kt`) |
| :core-agent | `android/core-agent/src/main/java/ai/eqo/core/agent/AutoReplyEngine.kt` | MOVE | `ai.eqo.core.agent` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/core/agent/AutoReplyEngine.kt`) |
| :core-agent | `android/core-agent/src/main/java/ai/eqo/core/agent/ContactResolver.kt` | MOVE | `ai.eqo.core.agent` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/core/agent/ContactResolver.kt`) |
| :core-agent | `android/core-agent/src/main/java/ai/eqo/core/agent/PlanManager.kt` | MOVE | `ai.eqo.core.agent` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/core/agent/PlanManager.kt`) |
| :core-agent | `android/core-agent/src/main/java/ai/eqo/core/agent/PlanValidator.kt` | MOVE | `ai.eqo.core.agent` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/core/agent/PlanValidator.kt`) |
| :core-agent | `android/core-agent/src/main/java/ai/eqo/core/agent/ReEvaluationEngine.kt` | MOVE | `ai.eqo.core.agent` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/core/agent/ReEvaluationEngine.kt`) |
| :core-agent | `android/core-agent/src/main/java/ai/eqo/core/agent/ReplyDispatcher.kt` | MOVE | `ai.eqo.core.agent` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/core/agent/ReplyDispatcher.kt`) |
| :core-agent | `android/core-agent/src/main/java/ai/eqo/core/agent/SmsComposePolicy.kt` | EQO-NEW | `ai.eqo.core.agent` | imbalki/project-eqo (EQO-authored, TASK-014 issue #19) |
| :core-agent | `android/core-agent/src/main/java/ai/eqo/core/service/NotificationListenerBridge.kt` | ADAPTER | `ai.eqo.core.service` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (adapter interface, upstream name kept) (upstream symbol: `com.opendroid.ai.core.service.OpenDroidNotificationListener`) |
| :core-agent | `android/core-agent/src/main/java/ai/eqo/core/crash/CrashLogRedactor.kt` | MOVE | `ai.eqo.core.crash` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/core/crash/CrashLogRedactor.kt`) |
| :core-agent | `android/core-agent/src/main/java/ai/eqo/core/memory/ExecutionHistoryPrivacy.kt` | MOVE | `ai.eqo.core.memory` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/core/memory/ExecutionHistoryPrivacy.kt`) |
| :core-agent | `android/core-agent/src/main/java/ai/eqo/core/memory/MemoryStore.kt` | ADAPTER | `ai.eqo.core.memory` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (adapter interface, upstream name kept) (upstream symbol: `com.opendroid.ai.core.memory.MemoryManager`) |
| :core-agent | `android/core-agent/src/main/java/ai/eqo/core/memory/NotificationStore.kt` | ADAPTER | `ai.eqo.core.memory` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (adapter interface, upstream name kept) (upstream symbol: `com.opendroid.ai.core.memory.NotificationIntelligence`) |
| :core-agent | `android/core-agent/src/main/java/ai/eqo/core/memory/WorkingMemory.kt` | MOVE | `ai.eqo.core.memory` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/core/memory/WorkingMemory.kt`) |
| :core-agent | `android/core-agent/src/main/java/ai/eqo/data/repository/ChatHistoryStore.kt` | ADAPTER | `ai.eqo.data.repository` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (adapter interface, upstream name kept) (upstream symbol: `com.opendroid.ai.data.repository.ConversationRepository`) |
| :core-agent | `android/core-agent/src/main/java/ai/eqo/data/repository/PlanStore.kt` | ADAPTER | `ai.eqo.data.repository` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (adapter interface, upstream name kept) (upstream symbol: `com.opendroid.ai.data.repository.PlanRepository`) |
| :core-agent | `android/core-agent/src/test/java/ai/eqo/core/agent/ActionSequenceExecutorTest.kt` | MOVE | `ai.eqo.core.agent` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/test/java/com/opendroid/ai/core/agent/ActionSequenceExecutorTest.kt`) |
| :core-agent | `android/core-agent/src/test/java/ai/eqo/core/agent/AutoApprovalPolicyTest.kt` | MOVE | `ai.eqo.core.agent` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/test/java/com/opendroid/ai/core/agent/AutoApprovalPolicyTest.kt`) |
| :core-agent | `android/core-agent/src/test/java/ai/eqo/core/agent/ReplyDispatcherSmsComposeTest.kt` | EQO-NEW | `ai.eqo.core.agent` | imbalki/project-eqo (EQO-authored, TASK-014 issue #19) |
| :core-agent | `android/core-agent/src/test/java/ai/eqo/core/agent/SmsComposePolicyTest.kt` | EQO-NEW | `ai.eqo.core.agent` | imbalki/project-eqo (EQO-authored, TASK-014 issue #19) |
| :core-agent | `android/core-agent/src/test/java/ai/eqo/core/agent/SmsTermuxToolHiddenTest.kt` | EQO-NEW | `ai.eqo.core.agent` | imbalki/project-eqo (EQO-authored, TASK-014 issue #19) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/NotificationTapTarget.kt` | ADAPTER | `ai.eqo` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (adapter interface, upstream name kept) (upstream symbol: `com.opendroid.ai.MainActivity`) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/actions/ActionAutoMapper.kt` | ADAPTER | `ai.eqo.actions` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (adapter interface, upstream name kept) (upstream symbol: `com.opendroid.ai.actions.ActionAutoMapper`) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/actions/ActionDispatcher.kt` | MOVE | `ai.eqo.actions` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/actions/ActionDispatcher.kt`) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/actions/AdvancedControlActions.kt` | ADAPTER | `ai.eqo.actions` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (adapter interface, upstream name kept) (upstream symbol: `com.opendroid.ai.actions.AdvancedControlActions`) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/actions/CalendarActions.kt` | ADAPTER | `ai.eqo.actions` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (adapter interface, upstream name kept) (upstream symbol: `com.opendroid.ai.actions.CalendarActions`) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/actions/CommunicationActions.kt` | ADAPTER | `ai.eqo.actions` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (adapter interface, upstream name kept) (upstream symbol: `com.opendroid.ai.actions.CommunicationActions`) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/actions/FinanceActions.kt` | ADAPTER | `ai.eqo.actions` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (adapter interface, upstream name kept) (upstream symbol: `com.opendroid.ai.actions.FinanceActions`) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/actions/FoodShoppingActions.kt` | ADAPTER | `ai.eqo.actions` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (adapter interface, upstream name kept) (upstream symbol: `com.opendroid.ai.actions.FoodShoppingActions`) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/actions/InformationActions.kt` | ADAPTER | `ai.eqo.actions` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (adapter interface, upstream name kept) (upstream symbol: `com.opendroid.ai.actions.InformationActions`) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/actions/MacroActions.kt` | ADAPTER | `ai.eqo.actions` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (adapter interface, upstream name kept) (upstream symbol: `com.opendroid.ai.actions.MacroActions`) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/actions/MediaActions.kt` | ADAPTER | `ai.eqo.actions` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (adapter interface, upstream name kept) (upstream symbol: `com.opendroid.ai.actions.MediaActions`) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/actions/NotificationActions.kt` | ADAPTER | `ai.eqo.actions` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (adapter interface, upstream name kept) (upstream symbol: `com.opendroid.ai.actions.NotificationActions`) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/actions/RoutineActions.kt` | ADAPTER | `ai.eqo.actions` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (adapter interface, upstream name kept) (upstream symbol: `com.opendroid.ai.actions.RoutineActions`) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/actions/SmartHomeActions.kt` | ADAPTER | `ai.eqo.actions` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (adapter interface, upstream name kept) (upstream symbol: `com.opendroid.ai.actions.SmartHomeActions`) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/actions/SocialActions.kt` | ADAPTER | `ai.eqo.actions` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (adapter interface, upstream name kept) (upstream symbol: `com.opendroid.ai.actions.SocialActions`) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/actions/SystemActions.kt` | ADAPTER | `ai.eqo.actions` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (minimal action interface, upstream name kept) (upstream symbol: `com.opendroid.ai.actions.SystemActions`) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/actions/TransportActions.kt` | ADAPTER | `ai.eqo.actions` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (adapter interface, upstream name kept) (upstream symbol: `com.opendroid.ai.actions.TransportActions`) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/actions/base/Action.kt` | MOVE | `ai.eqo.actions.base` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/actions/base/Action.kt`) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/actions/base/ActionResult.kt` | MOVE | `ai.eqo.actions.base` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/actions/base/ActionResult.kt`) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/core/agent/ActionRisk.kt` | MOVE | `ai.eqo.core.agent` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/core/agent/ActionRisk.kt`) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/core/agent/ActionSchema.kt` | MOVE | `ai.eqo.core.agent` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/core/agent/ActionSchema.kt`) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/core/agent/AliasResolver.kt` | MOVE | `ai.eqo.core.agent` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/core/agent/AliasResolver.kt`) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/core/agent/ChatErrorUiState.kt` | MOVE | `ai.eqo.core.agent` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/core/agent/ChatErrorUiState.kt`) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/core/agent/DeviceStateProvider.kt` | MOVE | `ai.eqo.core.agent` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/core/agent/DeviceStateProvider.kt`) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/core/agent/IntentClassifier.kt` | MOVE | `ai.eqo.core.agent` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/core/agent/IntentClassifier.kt`) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/core/llm/ClaudeModelCatalog.kt` | MOVE | `ai.eqo.core.llm` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/core/llm/ClaudeModelCatalog.kt`) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/core/llm/ConnectionTest.kt` | MOVE | `ai.eqo.core.llm` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/core/llm/ConnectionTest.kt`) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/core/llm/ConnectionTestRunner.kt` | EQO-NEW | `ai.eqo.core.llm` | imbalki/project-eqo (EQO-authored, TASK-006 issue #11) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/core/llm/LLMProvider.kt` | MOVE | `ai.eqo.core.llm` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/core/llm/LLMProvider.kt`) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/core/llm/LLMProviderFactory.kt` | MOVE | `ai.eqo.core.llm` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/core/llm/LLMProviderFactory.kt`) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/core/llm/LiteRtCompatibility.kt` | MOVE | `ai.eqo.core.llm` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/core/llm/LiteRtCompatibility.kt`) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/core/llm/ModelArtifactIntegrity.kt` | MOVE | `ai.eqo.core.llm` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/core/llm/ModelArtifactIntegrity.kt`) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/core/llm/ModelDownloadForegroundInfoFactory.kt` | MOVE | `ai.eqo.core.llm` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/core/llm/ModelDownloadForegroundInfoFactory.kt`) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/core/llm/ModelDownloadRetryPolicy.kt` | MOVE | `ai.eqo.core.llm` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/core/llm/ModelDownloadRetryPolicy.kt`) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/core/llm/ModelDownloadStopReason.kt` | MOVE | `ai.eqo.core.llm` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/core/llm/ModelDownloadStopReason.kt`) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/core/llm/ModelDownloadWorkRequest.kt` | MOVE | `ai.eqo.core.llm` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/core/llm/ModelDownloadWorkRequest.kt`) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/core/llm/ModelDownloadWorker.kt` | ADAPTER | `ai.eqo.core.llm` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream worker replaced with D-009 fail-closed study worker, issue #20) |
| :core-llm | `android/core-llm/src/test/java/ai/eqo/core/llm/ParkedModelDownloadWorkerTest.kt` | EQO-NEW | `ai.eqo.core.llm` | imbalki/project-eqo (EQO-authored, issue #20 parked-worker regression) |
| :app | `android/app/src/test/kotlin/ai/eqo/StudyWorkerWiringTest.kt` | EQO-NEW | `ai.eqo` | imbalki/project-eqo (EQO-authored, issue #20 plain-application worker guard) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/core/llm/ModelFetcher.kt` | MOVE | `ai.eqo.core.llm` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/core/llm/ModelFetcher.kt`) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/core/llm/ModelManager.kt` | MOVE | `ai.eqo.core.llm` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/core/llm/ModelManager.kt`) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/core/llm/ModelStoragePaths.kt` | MOVE | `ai.eqo.core.llm` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/core/llm/ModelStoragePaths.kt`) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/core/llm/OnDeviceLatencyProfile.kt` | MOVE | `ai.eqo.core.llm` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/core/llm/OnDeviceLatencyProfile.kt`) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/core/llm/OnDeviceModelRegistry.kt` | MOVE | `ai.eqo.core.llm` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/core/llm/OnDeviceModelRegistry.kt`) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/core/llm/PromptBudget.kt` | MOVE | `ai.eqo.core.llm` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/core/llm/PromptBudget.kt`) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/core/llm/ProviderCatalog.kt` | MOVE | `ai.eqo.core.llm` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/core/llm/ProviderCatalog.kt`) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/core/llm/ProviderSelectionPolicy.kt` | MOVE | `ai.eqo.core.llm` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/core/llm/ProviderSelectionPolicy.kt`) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/core/llm/error/LLMError.kt` | MOVE | `ai.eqo.core.llm.error` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/core/llm/error/LLMError.kt`) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/core/llm/error/ProviderErrorDetail.kt` | MOVE | `ai.eqo.core.llm.error` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/core/llm/error/ProviderErrorDetail.kt`) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/core/llm/prompts/AutoReplyPrompts.kt` | MOVE | `ai.eqo.core.llm.prompts` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/core/llm/prompts/AutoReplyPrompts.kt`) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/core/llm/prompts/PlanningPrompts.kt` | MOVE | `ai.eqo.core.llm.prompts` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/core/llm/prompts/PlanningPrompts.kt`) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/core/llm/prompts/ReEvalPrompts.kt` | MOVE | `ai.eqo.core.llm.prompts` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/core/llm/prompts/ReEvalPrompts.kt`) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/core/llm/prompts/SystemPrompts.kt` | MOVE | `ai.eqo.core.llm.prompts` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/core/llm/prompts/SystemPrompts.kt`) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/core/llm/providers/ClaudeProvider.kt` | MOVE | `ai.eqo.core.llm.providers` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/core/llm/providers/ClaudeProvider.kt`) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/core/llm/providers/CohereProvider.kt` | MOVE | `ai.eqo.core.llm.providers` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/core/llm/providers/CohereProvider.kt`) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/core/llm/providers/CopilotProvider.kt` | MOVE | `ai.eqo.core.llm.providers` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/core/llm/providers/CopilotProvider.kt`) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/core/llm/providers/CustomOpenAIProvider.kt` | MOVE | `ai.eqo.core.llm.providers` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/core/llm/providers/CustomOpenAIProvider.kt`) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/core/llm/providers/DeepSeekProvider.kt` | MOVE | `ai.eqo.core.llm.providers` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/core/llm/providers/DeepSeekProvider.kt`) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/core/llm/providers/GeminiProvider.kt` | MOVE | `ai.eqo.core.llm.providers` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/core/llm/providers/GeminiProvider.kt`) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/core/llm/providers/GemmaProvider.kt` | MOVE | `ai.eqo.core.llm.providers` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/core/llm/providers/GemmaProvider.kt`) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/core/llm/providers/GroqProvider.kt` | MOVE | `ai.eqo.core.llm.providers` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/core/llm/providers/GroqProvider.kt`) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/core/llm/providers/HybridOnDeviceProvider.kt` | MOVE | `ai.eqo.core.llm.providers` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/core/llm/providers/HybridOnDeviceProvider.kt`) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/core/llm/providers/LiteRTLMProvider.kt` | MOVE | `ai.eqo.core.llm.providers` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/core/llm/providers/LiteRTLMProvider.kt`) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/core/llm/providers/MistralProvider.kt` | MOVE | `ai.eqo.core.llm.providers` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/core/llm/providers/MistralProvider.kt`) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/core/llm/providers/OllamaProvider.kt` | MOVE | `ai.eqo.core.llm.providers` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/core/llm/providers/OllamaProvider.kt`) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/core/llm/providers/OpenAIProvider.kt` | MOVE | `ai.eqo.core.llm.providers` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/core/llm/providers/OpenAIProvider.kt`) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/core/llm/providers/OpenRouterProvider.kt` | MOVE | `ai.eqo.core.llm.providers` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/core/llm/providers/OpenRouterProvider.kt`) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/core/llm/providers/TogetherAIProvider.kt` | MOVE | `ai.eqo.core.llm.providers` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/core/llm/providers/TogetherAIProvider.kt`) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/core/llm/security/LogRedactor.kt` | EQO-NEW | `ai.eqo.core.llm.security` | imbalki/project-eqo (EQO-authored, TASK-006 issue #11) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/core/llm/security/LogRedactorCrashHook.kt` | EQO-NEW | `ai.eqo.core.llm.security` | imbalki/project-eqo (EQO-authored, TASK-006 issue #11) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/core/llm/security/RedactingLog.kt` | EQO-NEW | `ai.eqo.core.llm.security` | imbalki/project-eqo (EQO-authored, TASK-006 issue #11) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/core/util/DeviceCapabilities.kt` | MOVE | `ai.eqo.core.util` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/core/util/DeviceCapabilities.kt`) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/core/util/DurationParser.kt` | MOVE | `ai.eqo.core.util` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/core/util/DurationParser.kt`) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/core/util/NetworkErrorFormatter.kt` | MOVE | `ai.eqo.core.util` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/core/util/NetworkErrorFormatter.kt`) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/core/util/UrlUtils.kt` | MOVE | `ai.eqo.core.util` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/core/util/UrlUtils.kt`) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/data/db/dao/ModelDao.kt` | MOVE | `ai.eqo.data.db.dao` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/data/db/dao/ModelDao.kt`) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/data/db/dao/NotificationDao.kt` | MOVE | `ai.eqo.data.db.dao` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/data/db/dao/NotificationDao.kt`) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/data/db/dao/UnknownActionDao.kt` | MOVE | `ai.eqo.data.db.dao` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/data/db/dao/UnknownActionDao.kt`) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/data/db/entities/ModelEntity.kt` | MOVE | `ai.eqo.data.db.entities` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/data/db/entities/ModelEntity.kt`) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/data/db/entities/NotificationEntity.kt` | MOVE | `ai.eqo.data.db.entities` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/data/db/entities/NotificationEntity.kt`) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/data/db/entities/UnknownActionEntity.kt` | MOVE | `ai.eqo.data.db.entities` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/data/db/entities/UnknownActionEntity.kt`) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/data/models/AutoMode.kt` | MOVE | `ai.eqo.data.models` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/data/models/AutoMode.kt`) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/data/models/AutoReplyConfig.kt` | MOVE | `ai.eqo.data.models` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/data/models/AutoReplyConfig.kt`) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/data/models/ChatMessage.kt` | MOVE | `ai.eqo.data.models` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/data/models/ChatMessage.kt`) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/data/models/HabitEvent.kt` | MOVE | `ai.eqo.data.models` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/data/models/HabitEvent.kt`) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/data/models/HabitRoutine.kt` | MOVE | `ai.eqo.data.models` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/data/models/HabitRoutine.kt`) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/data/models/LLMConfig.kt` | MOVE | `ai.eqo.data.models` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/data/models/LLMConfig.kt`) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/data/models/Macro.kt` | MOVE | `ai.eqo.data.models` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/data/models/Macro.kt`) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/data/models/Memory.kt` | MOVE | `ai.eqo.data.models` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/data/models/Memory.kt`) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/data/models/Plan.kt` | MOVE | `ai.eqo.data.models` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/data/models/Plan.kt`) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/data/models/PlanStep.kt` | MOVE | `ai.eqo.data.models` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/data/models/PlanStep.kt`) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/data/models/StepResult.kt` | MOVE | `ai.eqo.data.models` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/data/models/StepResult.kt`) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/data/models/UserProfile.kt` | MOVE | `ai.eqo.data.models` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/data/models/UserProfile.kt`) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/data/repository/ModelStore.kt` | ADAPTER | `ai.eqo.data.repository` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (adapter interface, upstream name kept) (upstream symbol: `com.opendroid.ai.data.repository.ModelRepository`) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/data/repository/SettingsRepository.kt` | MOVE | `ai.eqo.data.repository` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/data/repository/SettingsRepository.kt`) |
| :core-llm | `android/core-llm/src/test/java/ai/eqo/actions/base/ActionResultTest.kt` | MOVE | `ai.eqo.actions.base` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/test/java/com/opendroid/ai/actions/base/ActionResultTest.kt`) |
| :core-llm | `android/core-llm/src/test/java/ai/eqo/core/agent/ActionRiskPolicyTest.kt` | MOVE | `ai.eqo.core.agent` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/test/java/com/opendroid/ai/core/agent/ActionRiskPolicyTest.kt`) |
| :core-llm | `android/core-llm/src/test/java/ai/eqo/core/agent/ActionSchemaTest.kt` | MOVE | `ai.eqo.core.agent` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/test/java/com/opendroid/ai/core/agent/ActionSchemaTest.kt`) |
| :core-llm | `android/core-llm/src/test/java/ai/eqo/core/agent/AliasResolverTest.kt` | MOVE | `ai.eqo.core.agent` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/test/java/com/opendroid/ai/core/agent/AliasResolverTest.kt`) |
| :core-llm | `android/core-llm/src/test/java/ai/eqo/core/agent/ChatErrorUiStateTest.kt` | MOVE | `ai.eqo.core.agent` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/test/java/com/opendroid/ai/core/agent/ChatErrorUiStateTest.kt`) |
| :core-llm | `android/core-llm/src/test/java/ai/eqo/core/agent/NeverAutoApproveTest.kt` | MOVE | `ai.eqo.core.agent` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/test/java/com/opendroid/ai/core/agent/NeverAutoApproveTest.kt`) |
| :core-llm | `android/core-llm/src/test/java/ai/eqo/core/llm/ClaudeModelCatalogTest.kt` | MOVE | `ai.eqo.core.llm` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/test/java/com/opendroid/ai/core/llm/ClaudeModelCatalogTest.kt`) |
| :core-llm | `android/core-llm/src/test/java/ai/eqo/core/llm/ConnectionTestPlannerTest.kt` | MOVE | `ai.eqo.core.llm` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/test/java/com/opendroid/ai/core/llm/ConnectionTestPlannerTest.kt`) |
| :core-llm | `android/core-llm/src/test/java/ai/eqo/core/llm/ConnectionTestRunnerEndpointSecurityTest.kt` | EQO-NEW | `ai.eqo.core.llm` | imbalki/project-eqo (EQO-authored, TASK-006 issue #11) |
| :core-llm | `android/core-llm/src/test/java/ai/eqo/core/llm/LiteRtCompatibilityTest.kt` | MOVE | `ai.eqo.core.llm` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/test/java/com/opendroid/ai/core/llm/LiteRtCompatibilityTest.kt`) |
| :core-llm | `android/core-llm/src/test/java/ai/eqo/core/llm/ModelArtifactIntegrityTest.kt` | MOVE | `ai.eqo.core.llm` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/test/java/com/opendroid/ai/core/llm/ModelArtifactIntegrityTest.kt`) |
| :core-llm | `android/core-llm/src/test/java/ai/eqo/core/llm/ModelDownloadSchedulingTest.kt` | MOVE | `ai.eqo.core.llm` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/test/java/com/opendroid/ai/core/llm/ModelDownloadSchedulingTest.kt`) |
| :core-llm | `android/core-llm/src/test/java/ai/eqo/core/llm/ModelFetcherSecurityTest.kt` | EQO-NEW | `ai.eqo.core.llm` | imbalki/project-eqo (EQO-authored, TASK-006 issue #11) |
| :core-llm | `android/core-llm/src/test/java/ai/eqo/core/llm/ModelListParsersTest.kt` | MOVE | `ai.eqo.core.llm` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/test/java/com/opendroid/ai/core/llm/ModelListParsersTest.kt`) |
| :core-llm | `android/core-llm/src/test/java/ai/eqo/core/llm/ModelStoragePathsTest.kt` | MOVE | `ai.eqo.core.llm` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/test/java/com/opendroid/ai/core/llm/ModelStoragePathsTest.kt`) |
| :core-llm | `android/core-llm/src/test/java/ai/eqo/core/llm/OnDeviceLatencyProfileTest.kt` | MOVE | `ai.eqo.core.llm` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/test/java/com/opendroid/ai/core/llm/OnDeviceLatencyProfileTest.kt`) |
| :core-llm | `android/core-llm/src/test/java/ai/eqo/core/llm/OnDeviceModelRegistryCustomTest.kt` | MOVE | `ai.eqo.core.llm` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/test/java/com/opendroid/ai/core/llm/OnDeviceModelRegistryCustomTest.kt`) |
| :core-llm | `android/core-llm/src/test/java/ai/eqo/core/llm/PromptBudgetTest.kt` | MOVE | `ai.eqo.core.llm` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/test/java/com/opendroid/ai/core/llm/PromptBudgetTest.kt`) |
| :core-llm | `android/core-llm/src/test/java/ai/eqo/core/llm/ProviderCatalogTest.kt` | MOVE | `ai.eqo.core.llm` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/test/java/com/opendroid/ai/core/llm/ProviderCatalogTest.kt`) |
| :core-llm | `android/core-llm/src/test/java/ai/eqo/core/llm/ProviderSelectionPolicyTest.kt` | MOVE | `ai.eqo.core.llm` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/test/java/com/opendroid/ai/core/llm/ProviderSelectionPolicyTest.kt`) |
| :core-llm | `android/core-llm/src/test/java/ai/eqo/core/llm/WrappedLLMProviderTest.kt` | MOVE | `ai.eqo.core.llm` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/test/java/com/opendroid/ai/core/llm/WrappedLLMProviderTest.kt`) |
| :core-llm | `android/core-llm/src/test/java/ai/eqo/core/llm/error/LLMErrorMapperTest.kt` | MOVE | `ai.eqo.core.llm.error` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/test/java/com/opendroid/ai/core/llm/error/LLMErrorMapperTest.kt`) |
| :core-llm | `android/core-llm/src/test/java/ai/eqo/core/llm/error/ProviderErrorDetailTest.kt` | MOVE | `ai.eqo.core.llm.error` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/test/java/com/opendroid/ai/core/llm/error/ProviderErrorDetailTest.kt`) |
| :core-llm | `android/core-llm/src/test/java/ai/eqo/core/llm/prompts/SmsComposePromptContractTest.kt` | EQO-NEW | `ai.eqo.core.llm.prompts` | imbalki/project-eqo (EQO-authored, TASK-014 issue #19) |
| :core-llm | `android/core-llm/src/test/java/ai/eqo/core/llm/providers/CustomOpenAIProviderNetworkTest.kt` | MOVE | `ai.eqo.core.llm.providers` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/test/java/com/opendroid/ai/core/llm/providers/CustomOpenAIProviderNetworkTest.kt`) |
| :core-llm | `android/core-llm/src/test/java/ai/eqo/core/llm/providers/OpenRouterConnectionTestTest.kt` | EQO-NEW | `ai.eqo.core.llm.providers` | imbalki/project-eqo (EQO-authored, TASK-006 issue #11) |
| :core-llm | `android/core-llm/src/test/java/ai/eqo/core/llm/providers/OpenRouterProviderRedactionTest.kt` | EQO-NEW | `ai.eqo.core.llm.providers` | imbalki/project-eqo (EQO-authored, TASK-006 issue #11) |
| :core-llm | `android/core-llm/src/test/java/ai/eqo/core/llm/security/LogRedactorCrashHookTest.kt` | EQO-NEW | `ai.eqo.core.llm.security` | imbalki/project-eqo (EQO-authored, TASK-006 issue #11) |
| :core-llm | `android/core-llm/src/test/java/ai/eqo/core/llm/security/LogRedactorTest.kt` | EQO-NEW | `ai.eqo.core.llm.security` | imbalki/project-eqo (EQO-authored, TASK-006 issue #11) |
| :core-llm | `android/core-llm/src/test/java/ai/eqo/core/llm/security/RedactingLogTest.kt` | EQO-NEW | `ai.eqo.core.llm.security` | imbalki/project-eqo (EQO-authored, TASK-006 issue #11) |
| :core-llm | `android/core-llm/src/test/java/ai/eqo/core/util/DeviceCapabilitiesTest.kt` | MOVE | `ai.eqo.core.util` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/test/java/com/opendroid/ai/core/util/DeviceCapabilitiesTest.kt`) |
| :core-llm | `android/core-llm/src/test/java/ai/eqo/core/util/DurationParserTest.kt` | MOVE | `ai.eqo.core.util` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/test/java/com/opendroid/ai/core/util/DurationParserTest.kt`) |
| :core-llm | `android/core-llm/src/test/java/ai/eqo/core/util/NetworkErrorFormatterSecurityTest.kt` | EQO-NEW | `ai.eqo.core.util` | imbalki/project-eqo (EQO-authored, TASK-006 issue #11) |
| :core-llm | `android/core-llm/src/test/java/ai/eqo/data/models/AutoModeConfigTest.kt` | MOVE | `ai.eqo.data.models` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/test/java/com/opendroid/ai/data/models/AutoModeConfigTest.kt`) |
| :core-llm | `android/core-llm/src/test/java/ai/eqo/data/models/LLMConfigRedactionTest.kt` | EQO-NEW | `ai.eqo.data.models` | imbalki/project-eqo (EQO-authored, TASK-006 issue #11) |
| :core-llm | `android/core-llm/src/test/java/ai/eqo/data/repository/SettingsRepositoryProviderCredentialsTest.kt` | MOVE | `ai.eqo.data.repository` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/test/java/com/opendroid/ai/data/repository/SettingsRepositoryProviderCredentialsTest.kt`) |
| :core-security | `android/core-security/src/main/java/ai/eqo/core/security/KeystoreSecretStorage.kt` | MOVE | `ai.eqo.core.security` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/core/security/KeystoreSecretStorage.kt`) |
| :core-security | `android/core-security/src/main/java/ai/eqo/core/security/LegacyPreferenceMigration.kt` | MOVE | `ai.eqo.core.security` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/core/security/LegacyPreferenceMigration.kt`) |
| :core-security | `android/core-security/src/main/java/ai/eqo/core/security/LegacySecurePreferenceInventory.kt` | MOVE | `ai.eqo.core.security` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/core/security/LegacySecurePreferenceInventory.kt`) |
| :core-security | `android/core-security/src/main/java/ai/eqo/core/security/ProviderCostDisclosure.kt` | EQO-NEW | `ai.eqo.core.security` | imbalki/project-eqo (EQO-authored, TASK-006 issue #11) |
| :core-security | `android/core-security/src/main/java/ai/eqo/core/security/ProviderCredentialStore.kt` | MOVE | `ai.eqo.core.security` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/core/security/ProviderCredentialStore.kt`) |
| :core-security | `android/core-security/src/main/java/ai/eqo/core/security/SensitiveMemoryStore.kt` | MOVE | `ai.eqo.core.security` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/core/security/SensitiveMemoryStore.kt`) |
| :core-security | `android/core-security/src/main/java/ai/eqo/core/security/SocialCredentialStore.kt` | MOVE | `ai.eqo.core.security` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/core/security/SocialCredentialStore.kt`) |
| :core-security | `android/core-security/src/main/java/ai/eqo/core/security/UserProfileStore.kt` | MOVE | `ai.eqo.core.security` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/core/security/UserProfileStore.kt`) |
| :core-security | `android/core-security/src/main/java/ai/eqo/core/settings/AppSettingsStore.kt` | MOVE | `ai.eqo.core.settings` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/core/settings/AppSettingsStore.kt`) |
| :core-security | `android/core-security/src/main/java/ai/eqo/social/domain/model/SocialModels.kt` | MOVE | `ai.eqo.social.domain.model` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/social/domain/model/SocialModels.kt`) |
| :core-security | `android/core-security/src/main/java/ai/eqo/social/domain/model/SocialPlatform.kt` | MOVE | `ai.eqo.social.domain.model` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/social/domain/model/SocialPlatform.kt`) |
| :core-security | `android/core-security/src/test/java/ai/eqo/core/security/LegacyPlaintextPreferencesSourceTest.kt` | EQO-NEW | `ai.eqo.core.security` | imbalki/project-eqo (EQO-authored; TASK-005 review R1 test) |
| :core-security | `android/core-security/src/test/java/ai/eqo/core/security/LegacySecurePreferencesRetirementTest.kt` | MOVE | `ai.eqo.core.security` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/test/java/com/opendroid/ai/core/security/LegacySecurePreferencesRetirementTest.kt`) |
| :core-security | `android/core-security/src/test/java/ai/eqo/core/security/OpenRouterKeyStorageTest.kt` | EQO-NEW | `ai.eqo.core.security` | imbalki/project-eqo (EQO-authored, TASK-006 issue #11) |
| :core-security | `android/core-security/src/test/java/ai/eqo/core/security/ProviderCostDisclosureGateTest.kt` | EQO-NEW | `ai.eqo.core.security` | imbalki/project-eqo (EQO-authored, TASK-006 issue #11) |
| :core-security | `android/core-security/src/test/java/ai/eqo/core/security/ProviderCredentialStoreTest.kt` | MOVE | `ai.eqo.core.security` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/test/java/com/opendroid/ai/core/security/ProviderCredentialStoreTest.kt`) |
| :platform-a11y | `android/platform-a11y/src/androidTest/java/ai/eqo/test/EqoDeviceRecordsDriver.kt` | EQO-NEW | `ai.eqo.test` | imbalki/project-eqo (EQO-authored, TASK-009 issue #14) |
| :platform-a11y | `android/platform-a11y/src/androidTest/java/ai/eqo/test/EqoDeviceRecordsTest.kt` | EQO-NEW | `ai.eqo.test` | imbalki/project-eqo (EQO-authored, TASK-009 issue #14) |
| :platform-a11y | `android/platform-a11y/src/androidTest/java/ai/eqo/test/EqoTestBindings.kt` | EQO-NEW | `ai.eqo.test` | imbalki/project-eqo (EQO-authored, TASK-009 issue #14) |
| :platform-a11y | `android/platform-a11y/src/androidTest/java/ai/eqo/test/EqoTestFakes.kt` | EQO-NEW | `ai.eqo.test` | imbalki/project-eqo (EQO-authored, TASK-009 issue #14) |
| :platform-a11y | `android/platform-a11y/src/androidTest/java/ai/eqo/test/EqoTestGraphAnchor.kt` | EQO-NEW | `ai.eqo.test` | imbalki/project-eqo (EQO-authored, TASK-009 issue #14) |
| :platform-a11y | `android/platform-a11y/src/androidTest/java/ai/eqo/test/EqoTestHostApplication.kt` | EQO-NEW | `ai.eqo.test` | imbalki/project-eqo (EQO-authored, TASK-009 issue #14) |
| :platform-a11y | `android/platform-a11y/src/androidTest/java/ai/eqo/test/EqoTestTargetActivity.kt` | EQO-NEW | `ai.eqo.test` | imbalki/project-eqo (EQO-authored, TASK-009 issue #14) |
| :platform-a11y | `android/platform-a11y/src/main/java/ai/eqo/accessibility/A11yNode.kt` | EQO-NEW | `ai.eqo.accessibility` | imbalki/project-eqo (EQO-authored, TASK-009 issue #14) |
| :platform-a11y | `android/platform-a11y/src/main/java/ai/eqo/accessibility/A11yResult.kt` | EQO-NEW | `ai.eqo.accessibility` | imbalki/project-eqo (EQO-authored, TASK-009 issue #14) |
| :platform-a11y | `android/platform-a11y/src/main/java/ai/eqo/accessibility/AccessibilityNodeTraversal.kt` | MOVE | `ai.eqo.accessibility` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/accessibility/AccessibilityNodeTraversal.kt`) |
| :platform-a11y | `android/platform-a11y/src/main/java/ai/eqo/accessibility/AccessibilitySetupGuide.kt` | EQO-NEW | `ai.eqo.accessibility` | imbalki/project-eqo (EQO-authored, TASK-009 issue #14) |
| :platform-a11y | `android/platform-a11y/src/main/java/ai/eqo/accessibility/CallFlowVerifier.kt` | MOVE | `ai.eqo.accessibility` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/accessibility/CallFlowVerifier.kt`) |
| :platform-a11y | `android/platform-a11y/src/main/java/ai/eqo/accessibility/EQOAccessibilityService.kt` | MOVE | `ai.eqo.accessibility` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/accessibility/EQOAccessibilityService.kt`) |
| :platform-a11y | `android/platform-a11y/src/main/java/ai/eqo/accessibility/EqoAutomation.kt` | EQO-NEW | `ai.eqo.accessibility` | imbalki/project-eqo (EQO-authored, TASK-009 issue #14) |
| :platform-a11y | `android/platform-a11y/src/main/java/ai/eqo/accessibility/GenericAppAutomator.kt` | MOVE | `ai.eqo.accessibility` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/accessibility/GenericAppAutomator.kt`) |
| :platform-a11y | `android/platform-a11y/src/main/java/ai/eqo/accessibility/NodeTreeSearch.kt` | EQO-NEW | `ai.eqo.accessibility` | imbalki/project-eqo (EQO-authored, TASK-009 issue #14) |
| :platform-a11y | `android/platform-a11y/src/main/java/ai/eqo/accessibility/SmsAutomator.kt` | MOVE | `ai.eqo.accessibility` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/accessibility/SmsAutomator.kt`) |
| :platform-a11y | `android/platform-a11y/src/main/java/ai/eqo/accessibility/TakeoverDetector.kt` | EQO-NEW | `ai.eqo.accessibility` | imbalki/project-eqo (EQO-authored, TASK-009 issue #14) |
| :platform-a11y | `android/platform-a11y/src/main/java/ai/eqo/accessibility/TelegramAutomator.kt` | MOVE | `ai.eqo.accessibility` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/accessibility/TelegramAutomator.kt`) |
| :platform-a11y | `android/platform-a11y/src/main/java/ai/eqo/accessibility/WhatsAppAutomator.kt` | MOVE | `ai.eqo.accessibility` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/accessibility/WhatsAppAutomator.kt`) |
| :platform-a11y | `android/platform-a11y/src/main/java/ai/eqo/core/agent/AgentLoop.kt` | MOVE | `ai.eqo.core.agent` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/core/agent/AgentLoop.kt`) |
| :platform-a11y | `android/platform-a11y/src/main/java/ai/eqo/core/agent/VisionEngine.kt` | MOVE | `ai.eqo.core.agent` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/main/java/com/opendroid/ai/core/agent/VisionEngine.kt`) |
| :platform-a11y | `android/platform-a11y/src/main/java/ai/eqo/core/routine/HabitRoutineTracker.kt` | ADAPTER | `ai.eqo.core.routine` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (adapter interface, upstream name kept) (upstream symbol: `com.opendroid.ai.core.routine.HabitRoutineEngine`) |
| :platform-a11y | `android/platform-a11y/src/main/java/ai/eqo/core/service/ServiceBridge.kt` | ADAPTER | `ai.eqo.core.service` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (adapter interface, upstream name kept) (upstream symbol: `com.opendroid.ai.core.service.OpenDroidService`) |
| :platform-a11y | `android/platform-a11y/src/test/java/ai/eqo/accessibility/EqoAutomationFakeTreeTest.kt` | EQO-NEW | `ai.eqo.accessibility` | imbalki/project-eqo (EQO-authored, TASK-009 issue #14) |
| :platform-a11y | `android/platform-a11y/src/test/java/ai/eqo/accessibility/EqoNeverGrantsTest.kt` | EQO-NEW | `ai.eqo.accessibility` | imbalki/project-eqo (EQO-authored, TASK-009 issue #14) |
| :platform-a11y | `android/platform-a11y/src/test/java/ai/eqo/accessibility/EqoSingleServiceManifestTest.kt` | EQO-NEW | `ai.eqo.accessibility` | imbalki/project-eqo (EQO-authored, TASK-009 issue #14) |
| :platform-a11y | `android/platform-a11y/src/test/java/ai/eqo/accessibility/FakeNode.kt` | EQO-NEW | `ai.eqo.accessibility` | imbalki/project-eqo (EQO-authored, TASK-009 issue #14) |
| :platform-a11y | `android/platform-a11y/src/test/java/ai/eqo/accessibility/OwnerPromptSafetyTest.kt` | EQO-NEW | `ai.eqo.accessibility` | imbalki/project-eqo (EQO-authored, TASK-009 issue #14) |
| :platform-a11y | `android/platform-a11y/src/test/java/ai/eqo/accessibility/TakeoverDetectorTest.kt` | EQO-NEW | `ai.eqo.accessibility` | imbalki/project-eqo (EQO-authored, TASK-009 issue #14) |
| :platform-a11y | `android/platform-a11y/src/test/java/ai/eqo/core/agent/NeedsInputParamKeyTest.kt` | MOVE | `ai.eqo.core.agent` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream path: `app/src/test/java/com/opendroid/ai/core/agent/NeedsInputParamKeyTest.kt`) |

(Count: 202 Kotlin files — 100% coverage of `git ls-files android/` *.kt.)

## TASK-007 additions (issue #12): forked privileged helper

Kind **FORK** = source forked from the privileged-helper upstreams (Shizuku server /
Shizuku-API) at the study commits pinned in `task-007-helper-spike.md` / FEASIBILITY-REPORT
§1, carrying an `// Origin:` header with the ORIGINAL upstream path. Fork deltas vs
upstream are the D-004 lockstep rename (application id, `moe.shizuku.manager.permission.*`
strings, binder extra key, provider-authority suffix, process name, starter binary name),
the EQO app-id allowlist replacing the manager-must-be-installed gate, and documented
spike cuts (rish remote shell, adb pairing native lib) - all itemised in
`task-007-helper-spike.md`. The Java files of the two helper modules are forked the same
way (this map covers Kotlin only, as before).

TASK-007 SF-1 / task-060 Java delta ledger (no Kotlin row/count changes):

- `helper-server/src/main/java/rikka/shizuku/server/ShizukuService.java`: FORK,
  RikkaApps/Shizuku @ `b844bc491f1790c72328e1a8e5b2349f8978f0ea`, original
  `server/src/main/java/rikka/shizuku/server/ShizukuService.java`; removed the manager
  secure-settings self-grant entirely, replacing the earlier best-effort grant fork delta.
  Original `Origin:` header retained; attach reply and non-fatal callback unchanged.
- `helper-server/src/test/java/rikka/shizuku/server/NoSecureSettingsWriteTest.java`:
  EQO-NEW, authored for issue #12; scans every module's main text sources/resources and
  manifests, with only comments excluded; negative fixtures are test-only.

| :helper-server | `android/helper-server/src/main/java/rikka/shizuku/server/ApkChangedObservers.kt` | FORK | `rikka.shizuku.server` | RikkaApps/Shizuku @ b844bc491f1790c72328e1a8e5b2349f8978f0ea (upstream path: `server/src/main/java/rikka/shizuku/server/ApkChangedObservers.kt`) |
| :helper-server | `android/helper-server/src/main/java/rikka/shizuku/server/ktx/Handler.kt` | FORK | `rikka.shizuku.server.ktx` | RikkaApps/Shizuku @ b844bc491f1790c72328e1a8e5b2349f8978f0ea (upstream path: `server/src/main/java/rikka/shizuku/server/ktx/Handler.kt`) |
| :helper-server | `android/helper-server/src/main/java/moe/shizuku/manager/ShizukuManagerProvider.kt` | FORK | `moe.shizuku.manager` | RikkaApps/Shizuku @ b844bc491f1790c72328e1a8e5b2349f8978f0ea (upstream path: `manager/src/main/java/moe/shizuku/manager/ShizukuManagerProvider.kt`; fork deltas: renamed binder extra key, manager Logger facade -> android.util.Log) |
| :helper-client | `android/helper-client/src/main/java/ai/eqo/helper/client/HelperActivationState.kt` | EQO-NEW | `ai.eqo.helper.client` | imbalki/project-eqo (EQO-authored, TASK-007 issue #12) |
| :helper-client | `android/helper-client/src/androidTest/java/ai/eqo/helper/client/MismatchedPermissionTest.kt` | EQO-NEW | `ai.eqo.helper.client` | imbalki/project-eqo (EQO-authored, TASK-007 issue #12) |
| :app | `android/app/src/androidTest/java/ai/eqo/helper/HelperSpikeDeviceTest.kt` | EQO-NEW | `ai.eqo.helper` | imbalki/project-eqo (EQO-authored, TASK-007 issue #12) |

(Count after TASK-007: 208 Kotlin files — 100% coverage of `git ls-files android/` *.kt., per `bash scripts/check-branding.sh` output `kt files: 208; provenance rows: 208`, exit 0.)

## TASK-012 additions (issue #17, action loop + security follow-ups)

The TASK-012 loop, gate, privacy and resume-confirmation files below are EQO-authored,
have no upstream counterpart, and are recorded as **EQO-NEW** rows.

| :core-agent | `android/core-agent/src/main/java/ai/eqo/core/agent/ActionLoop.kt` | EQO-NEW | `ai.eqo.core.agent` | imbalki/project-eqo (EQO-authored, TASK-012 issue #17) |
| :core-agent | `android/core-agent/src/main/java/ai/eqo/core/agent/ExecutedAction.kt` | EQO-NEW | `ai.eqo.core.agent` | imbalki/project-eqo (EQO-authored, TASK-012 issue #17) |
| :core-agent | `android/core-agent/src/main/java/ai/eqo/core/agent/LoopModel.kt` | EQO-NEW | `ai.eqo.core.agent` | imbalki/project-eqo (EQO-authored, TASK-012 issue #17) |
| :core-agent | `android/core-agent/src/main/java/ai/eqo/core/agent/SensitivityApprovalPolicy.kt` | EQO-NEW | `ai.eqo.core.agent` | imbalki/project-eqo (EQO-authored, TASK-012 issue #17) |
| :core-agent | `android/core-agent/src/main/java/ai/eqo/core/agent/StepVerifier.kt` | EQO-NEW | `ai.eqo.core.agent` | imbalki/project-eqo (EQO-authored, TASK-012 issue #17) |
| :core-agent | `android/core-agent/src/main/java/ai/eqo/core/agent/UserResumeConfirmation.kt` | EQO-NEW | `ai.eqo.core.agent` | imbalki/project-eqo (EQO-authored, TASK-012 issue #17) |
| :core-agent | `android/core-agent/src/test/java/ai/eqo/core/agent/ActionLoopDispatchTraceTest.kt` | EQO-NEW | `ai.eqo.core.agent` | imbalki/project-eqo (EQO-authored, race investigation #72) |
| :core-agent | `android/core-agent/src/test/java/ai/eqo/core/agent/DispatchTrace.kt` | EQO-NEW | `ai.eqo.core.agent` | imbalki/project-eqo (EQO-authored, race investigation #72) |
| :core-agent | `android/core-agent/src/test/java/ai/eqo/core/agent/ActionLoopRaceTest.kt` | EQO-NEW | `ai.eqo.core.agent` | imbalki/project-eqo (EQO-authored, TASK-012 issue #17) |
| :core-agent | `android/core-agent/src/test/java/ai/eqo/core/agent/ActionLoopResumeTest.kt` | EQO-NEW | `ai.eqo.core.agent` | imbalki/project-eqo (EQO-authored, TASK-012 issue #17) |
| :core-agent | `android/core-agent/src/test/java/ai/eqo/core/agent/ActionLoopSettleTest.kt` | EQO-NEW | `ai.eqo.core.agent` | imbalki/project-eqo (EQO-authored, TASK-012 issue #17) |
| :core-agent | `android/core-agent/src/test/java/ai/eqo/core/agent/ActionLoopTransitionsTest.kt` | EQO-NEW | `ai.eqo.core.agent` | imbalki/project-eqo (EQO-authored, TASK-012 issue #17) |
| :core-agent | `android/core-agent/src/test/java/ai/eqo/core/agent/LoopTestFixtures.kt` | EQO-NEW | `ai.eqo.core.agent` | imbalki/project-eqo (EQO-authored, TASK-012 issue #17) |
| :core-agent | `android/core-agent/src/test/java/ai/eqo/core/agent/SensitivityApprovalPolicyTest.kt` | EQO-NEW | `ai.eqo.core.agent` | imbalki/project-eqo (EQO-authored, TASK-012 issue #17) |
| :core-agent | `android/core-agent/src/test/java/ai/eqo/core/agent/StepVerifierTest.kt` | EQO-NEW | `ai.eqo.core.agent` | imbalki/project-eqo (EQO-authored, TASK-012 issue #17) |
| :platform-a11y | `android/platform-a11y/src/main/java/ai/eqo/accessibility/GatedServiceActions.kt` | EQO-NEW | `ai.eqo.accessibility` | imbalki/project-eqo (EQO-authored, TASK-012 issue #17) |
| :platform-a11y | `android/platform-a11y/src/main/java/ai/eqo/accessibility/ServiceActionOps.kt` | EQO-NEW | `ai.eqo.accessibility` | imbalki/project-eqo (EQO-authored, TASK-012 issue #17) |
| :platform-a11y | `android/platform-a11y/src/main/java/ai/eqo/accessibility/UntrustedScreenText.kt` | EQO-NEW | `ai.eqo.accessibility` | imbalki/project-eqo (EQO-authored, TASK-012 issue #17) |
| :platform-a11y | `android/platform-a11y/src/test/java/ai/eqo/accessibility/AutomatorsUseGatedRouteTest.kt` | EQO-NEW | `ai.eqo.accessibility` | imbalki/project-eqo (EQO-authored, TASK-012 issue #17) |
| :platform-a11y | `android/platform-a11y/src/test/java/ai/eqo/accessibility/GatedActionsGateTest.kt` | EQO-NEW | `ai.eqo.accessibility` | imbalki/project-eqo (EQO-authored, TASK-012 issue #17) |
| :platform-a11y | `android/platform-a11y/src/test/java/ai/eqo/accessibility/ScreenTextPrivacyTest.kt` | EQO-NEW | `ai.eqo.accessibility` | imbalki/project-eqo (EQO-authored, TASK-012 issue #17) |
| :platform-a11y | `android/platform-a11y/src/test/java/ai/eqo/accessibility/SmsAutomatorTakeoverGateTest.kt` | EQO-NEW | `ai.eqo.accessibility` | imbalki/project-eqo (EQO-authored, TASK-012 issue #17) |
| :platform-a11y | `android/platform-a11y/src/test/java/ai/eqo/accessibility/TakeoverSelfGestureTest.kt` | EQO-NEW | `ai.eqo.accessibility` | imbalki/project-eqo (EQO-authored, TASK-012 issue #17) |
| :platform-a11y | `android/platform-a11y/src/test/java/ai/eqo/accessibility/TakeoverAgentActionGraceTest.kt` | EQO-NEW | `ai.eqo.accessibility` | imbalki/project-eqo (EQO-authored, TASK-012 issue #17) |
| :platform-a11y | `android/platform-a11y/src/test/java/ai/eqo/accessibility/OwnWindowGuardTest.kt` | EQO-NEW | `ai.eqo.accessibility` | imbalki/project-eqo (EQO-authored, TASK-012 issue #17) |
| :platform-a11y | `android/platform-a11y/src/test/java/ai/eqo/accessibility/UntrustedScreenTextTest.kt` | EQO-NEW | `ai.eqo.accessibility` | imbalki/project-eqo (EQO-authored, TASK-012 issue #17) |
| :platform-a11y | `android/platform-a11y/src/test/java/ai/eqo/core/agent/TakeoverResumeUserOnlyTest.kt` | EQO-NEW | `ai.eqo.core.agent` | imbalki/project-eqo (EQO-authored, TASK-012 issue #17) |
| :platform-a11y | `android/platform-a11y/src/androidTest/java/ai/eqo/test/EqoActionLoopScenarioDriver.kt` | EQO-NEW | `ai.eqo.test` | imbalki/project-eqo (EQO-authored, TASK-012 issue #17) |

| :core-agent | `android/core-agent/src/test/java/ai/eqo/core/agent/SecurityReproRetryGateTest.kt` | EQO-NEW | `ai.eqo.core.agent` | imbalki/project-eqo (EQO security reviewer repro, TASK-012 issue #17, t_d306e5bb; syntax repaired, regression retained) |

## TASK-008 additions (issue #13)

Thirty-five TASK-008 files are recorded below. Fourteen are **EXTRACT** rows (twelve
production files plus two adapted tests): the
ClosePaw wireless-ADB pairing stack moved from `imoonkey/closepaw` @ `75dae2653f5a6b25d5df51ee7008b0f830de1536`
into the new `:adb-pairing` module. Unlike MOVE rows these are not byte-identical to the
donor: the package was renamed to `ai.eqo.adb.pairing`, the default peer label was
rebranded to `EQO`, the files were ktlint-formatted to this repo's style, and three
comment-only adjustments were made (blank line before donor block headers; inline
`/* autoClose = */` comment removed in AdbTlsClient.kt). Every extracted file carries an
`// Origin:` header naming the donor commit and upstream path. The full change ledger is
in `task-008-wireless-adb-pairing.md`. The remaining twenty-one rows are **EQO-NEW**:
the guided activation flow, the activation gate, the device-side runner, their tests,
and one app-side wiring test.

| Module | Current file (repo-relative) | Kind | Package | Upstream origin |
| --- | --- | --- | --- | --- |
| :adb-pairing | `android/adb-pairing/src/main/kotlin/ai/eqo/adb/pairing/AdbCryptoKeyStore.kt` | EXTRACT | `ai.eqo.adb.pairing` | imoonkey/closepaw @ 75dae2653f5a6b25d5df51ee7008b0f830de1536 (upstream path: `app/src/main/kotlin/ai/closepaw/browser/cdp/wireless/AdbCryptoKeyStore.kt`) |
| :adb-pairing | `android/adb-pairing/src/main/kotlin/ai/eqo/adb/pairing/AdbPairingClient.kt` | EXTRACT | `ai.eqo.adb.pairing` | imoonkey/closepaw @ 75dae2653f5a6b25d5df51ee7008b0f830de1536 (upstream path: `app/src/main/kotlin/ai/closepaw/browser/cdp/wireless/AdbPairingClient.kt`) |
| :adb-pairing | `android/adb-pairing/src/main/kotlin/ai/eqo/adb/pairing/AdbPairingPacket.kt` | EXTRACT | `ai.eqo.adb.pairing` | imoonkey/closepaw @ 75dae2653f5a6b25d5df51ee7008b0f830de1536 (upstream path: `app/src/main/kotlin/ai/closepaw/browser/cdp/wireless/AdbPairingPacket.kt`) |
| :adb-pairing | `android/adb-pairing/src/main/kotlin/ai/eqo/adb/pairing/AdbPairingTls.kt` | EXTRACT | `ai.eqo.adb.pairing` | imoonkey/closepaw @ 75dae2653f5a6b25d5df51ee7008b0f830de1536 (upstream path: `app/src/main/kotlin/ai/closepaw/browser/cdp/wireless/AdbPairingTls.kt`) |
| :adb-pairing | `android/adb-pairing/src/main/kotlin/ai/eqo/adb/pairing/AdbProtocol.kt` | EXTRACT | `ai.eqo.adb.pairing` | imoonkey/closepaw @ 75dae2653f5a6b25d5df51ee7008b0f830de1536 (upstream path: `app/src/main/kotlin/ai/closepaw/browser/cdp/wireless/AdbProtocol.kt`) |
| :adb-pairing | `android/adb-pairing/src/main/kotlin/ai/eqo/adb/pairing/AdbTlsClient.kt` | EXTRACT | `ai.eqo.adb.pairing` | imoonkey/closepaw @ 75dae2653f5a6b25d5df51ee7008b0f830de1536 (upstream path: `app/src/main/kotlin/ai/closepaw/browser/cdp/wireless/AdbTlsClient.kt`) |
| :adb-pairing | `android/adb-pairing/src/main/kotlin/ai/eqo/adb/pairing/AdbWireProtocolClient.kt` | EXTRACT | `ai.eqo.adb.pairing` | imoonkey/closepaw @ 75dae2653f5a6b25d5df51ee7008b0f830de1536 (upstream path: `app/src/main/kotlin/ai/closepaw/browser/cdp/wireless/AdbWireProtocolClient.kt`) |
| :adb-pairing | `android/adb-pairing/src/main/kotlin/ai/eqo/adb/pairing/AndroidPubkey.kt` | EXTRACT | `ai.eqo.adb.pairing` | imoonkey/closepaw @ 75dae2653f5a6b25d5df51ee7008b0f830de1536 (upstream path: `app/src/main/kotlin/ai/closepaw/browser/cdp/wireless/AndroidPubkey.kt`) |
| :adb-pairing | `android/adb-pairing/src/main/kotlin/ai/eqo/adb/pairing/PairOnceCache.kt` | EXTRACT | `ai.eqo.adb.pairing` | imoonkey/closepaw @ 75dae2653f5a6b25d5df51ee7008b0f830de1536 (upstream path: `app/src/main/kotlin/ai/closepaw/browser/cdp/wireless/PairOnceCache.kt`) |
| :adb-pairing | `android/adb-pairing/src/main/kotlin/ai/eqo/adb/pairing/Spake25519.kt` | EXTRACT | `ai.eqo.adb.pairing` | imoonkey/closepaw @ 75dae2653f5a6b25d5df51ee7008b0f830de1536 (upstream path: `app/src/main/kotlin/ai/closepaw/browser/cdp/wireless/Spake25519.kt`) |
| :adb-pairing | `android/adb-pairing/src/main/kotlin/ai/eqo/adb/pairing/TlsExporter.kt` | EXTRACT | `ai.eqo.adb.pairing` | imoonkey/closepaw @ 75dae2653f5a6b25d5df51ee7008b0f830de1536 (upstream path: `app/src/main/kotlin/ai/closepaw/browser/cdp/wireless/TlsExporter.kt`) |
| :adb-pairing | `android/adb-pairing/src/main/kotlin/ai/eqo/adb/pairing/WirelessAdbProviders.kt` | EXTRACT | `ai.eqo.adb.pairing` | imoonkey/closepaw @ 75dae2653f5a6b25d5df51ee7008b0f830de1536 (upstream path: `app/src/main/kotlin/ai/closepaw/browser/cdp/wireless/WirelessAdbProviders.kt`) |
| :adb-pairing | `android/adb-pairing/src/main/kotlin/ai/eqo/adb/pairing/ActivationCheck.kt` | EQO-NEW | `ai.eqo.adb.pairing` | imbalki/project-eqo (EQO-authored, TASK-008 issue #13) |
| :adb-pairing | `android/adb-pairing/src/main/kotlin/ai/eqo/adb/pairing/ActivationFailure.kt` | EQO-NEW | `ai.eqo.adb.pairing` | imbalki/project-eqo (EQO-authored, TASK-008 issue #13) |
| :adb-pairing | `android/adb-pairing/src/main/kotlin/ai/eqo/adb/pairing/ActivationGate.kt` | EQO-NEW | `ai.eqo.adb.pairing` | imbalki/project-eqo (EQO-authored, TASK-008 issue #13) |
| :adb-pairing | `android/adb-pairing/src/main/kotlin/ai/eqo/adb/pairing/ActivationSequence.kt` | EQO-NEW | `ai.eqo.adb.pairing` | imbalki/project-eqo (EQO-authored, TASK-008 issue #13) |
| :adb-pairing | `android/adb-pairing/src/main/kotlin/ai/eqo/adb/pairing/AdbPairingCode.kt` | EQO-NEW | `ai.eqo.adb.pairing` | imbalki/project-eqo (EQO-authored, TASK-008 issue #13) |
| :adb-pairing | `android/adb-pairing/src/main/kotlin/ai/eqo/adb/pairing/AndroidSettingsNames.kt` | EQO-NEW | `ai.eqo.adb.pairing` | imbalki/project-eqo (EQO-authored, TASK-008 issue #13) |
| :adb-pairing | `android/adb-pairing/src/main/kotlin/ai/eqo/adb/pairing/FailureClassifier.kt` | EQO-NEW | `ai.eqo.adb.pairing` | imbalki/project-eqo (EQO-authored, TASK-008 issue #13) |
| :adb-pairing | `android/adb-pairing/src/main/kotlin/ai/eqo/adb/pairing/PairingGuide.kt` | EQO-NEW | `ai.eqo.adb.pairing` | imbalki/project-eqo (EQO-authored, TASK-008 issue #13) |
| :adb-pairing | `android/adb-pairing/src/main/kotlin/ai/eqo/adb/pairing/WirelessAdbEndpoints.kt` | EQO-NEW | `ai.eqo.adb.pairing` | imbalki/project-eqo (EQO-authored, TASK-008 issue #13) |
| :adb-pairing | `android/adb-pairing/src/test/kotlin/ai/eqo/adb/pairing/AdbCryptoKeyStoreTest.kt` | EQO-NEW | `ai.eqo.adb.pairing` | imbalki/project-eqo (EQO-authored, TASK-008 issue #13; donor test concept, rewritten) |
| :adb-pairing | `android/adb-pairing/src/test/kotlin/ai/eqo/adb/pairing/AdbPairingCodeTest.kt` | EQO-NEW | `ai.eqo.adb.pairing` | imbalki/project-eqo (EQO-authored, TASK-008 issue #13) |
| :adb-pairing | `android/adb-pairing/src/test/kotlin/ai/eqo/adb/pairing/AdbPairingPacketTest.kt` | EXTRACT | `ai.eqo.adb.pairing` | imoonkey/closepaw @ 75dae2653f5a6b25d5df51ee7008b0f830de1536 (upstream path: `app/src/test/kotlin/ai/closepaw/browser/cdp/wireless/AdbPairingPacketTest.kt`; adapted to JUnit asserts) |
| :adb-pairing | `android/adb-pairing/src/test/kotlin/ai/eqo/adb/pairing/AdbProtocolTest.kt` | EXTRACT | `ai.eqo.adb.pairing` | imoonkey/closepaw @ 75dae2653f5a6b25d5df51ee7008b0f830de1536 (upstream path: `app/src/test/kotlin/ai/closepaw/browser/cdp/wireless/AdbProtocolTest.kt`; adapted to JUnit asserts) |
| :adb-pairing | `android/adb-pairing/src/test/kotlin/ai/eqo/adb/pairing/ActivationGateTest.kt` | EQO-NEW | `ai.eqo.adb.pairing` | imbalki/project-eqo (EQO-authored, TASK-008 issue #13) |
| :adb-pairing | `android/adb-pairing/src/test/kotlin/ai/eqo/adb/pairing/ActivationSequenceTest.kt` | EQO-NEW | `ai.eqo.adb.pairing` | imbalki/project-eqo (EQO-authored, TASK-008 issue #13) |
| :adb-pairing | `android/adb-pairing/src/test/kotlin/ai/eqo/adb/pairing/AndroidSettingsNamesTest.kt` | EQO-NEW | `ai.eqo.adb.pairing` | imbalki/project-eqo (EQO-authored, TASK-008 issue #13) |
| :adb-pairing | `android/adb-pairing/src/test/kotlin/ai/eqo/adb/pairing/FailureClassifierTest.kt` | EQO-NEW | `ai.eqo.adb.pairing` | imbalki/project-eqo (EQO-authored, TASK-008 issue #13) |
| :adb-pairing | `android/adb-pairing/src/test/kotlin/ai/eqo/adb/pairing/PairingGuideTest.kt` | EQO-NEW | `ai.eqo.adb.pairing` | imbalki/project-eqo (EQO-authored, TASK-008 issue #13) |
| :adb-pairing | `android/adb-pairing/src/test/kotlin/ai/eqo/adb/pairing/Spake25519RoundTripTest.kt` | EQO-NEW | `ai.eqo.adb.pairing` | imbalki/project-eqo (EQO-authored, TASK-008 issue #13) |
| :adb-pairing | `android/adb-pairing/src/test/kotlin/ai/eqo/adb/pairing/WirelessAdbEndpointsTest.kt` | EQO-NEW | `ai.eqo.adb.pairing` | imbalki/project-eqo (EQO-authored, TASK-008 issue #13) |
| :adb-pairing | `android/adb-pairing/src/main/kotlin/ai/eqo/adb/pairing/WirelessAdbActivationRunner.kt` | EQO-NEW | `ai.eqo.adb.pairing` | imbalki/project-eqo (EQO-authored, TASK-008 issue #13) |
| :adb-pairing | `android/adb-pairing/src/test/kotlin/ai/eqo/adb/pairing/WirelessAdbActivationRunnerTest.kt` | EQO-NEW | `ai.eqo.adb.pairing` | imbalki/project-eqo (EQO-authored, TASK-008 issue #13) |
| :adb-pairing | `android/adb-pairing/src/main/kotlin/ai/eqo/adb/pairing/LoopbackAdbHost.kt` | EQO-NEW | `ai.eqo.adb.pairing` | imbalki/project-eqo (EQO-authored, TASK-008 issue #13 alignment/security follow-up) |
| :adb-pairing | `android/adb-pairing/src/test/kotlin/ai/eqo/adb/pairing/LoopbackAdbHostTest.kt` | EQO-NEW | `ai.eqo.adb.pairing` | imbalki/project-eqo (EQO-authored, TASK-008 issue #13 alignment/security follow-up) |
| :app | `android/app/src/test/kotlin/ai/eqo/ActivationWiringTest.kt` | EQO-NEW | `ai.eqo` | imbalki/project-eqo (EQO-authored, TASK-008 issue #13) |

## TASK-010 additions (issue #15)

The eighteen TASK-010 Chrome DevTools (CDP) files in `:browser-cdp` are recorded below.
Seven are **EXTRACT** rows (imoonkey/closepaw @ `75dae2653f5a6b25d5df51ee7008b0f830de1536`,
package renamed to `ai.eqo.browser.cdp`; the shizuku/ sub-package is dropped and DevtoolsHttpProtocol
is re-homed into the flat package); eleven are **EQO-NEW** (the setup orchestrator, informed-consent
gate, typed setup errors, verified-endpoint probe, navigate/fill controller, and their tests).
Donor's synthetic dialog-query string and DevTools User-Agent are rebranded to EQO. See
`task-010-chrome-cdp-spike.md` for the full change ledger and the device test plan (PENDING owner
presence).

| :browser-cdp | `android/browser-cdp/src/main/kotlin/ai/eqo/browser/cdp/CdpTransport.kt` | EXTRACT | `ai.eqo.browser.cdp` | imoonkey/closepaw @ 75dae2653f5a6b25d5df51ee7008b0f830de1536 (upstream path: `app/src/main/kotlin/ai/closepaw/browser/cdp/CdpTransport.kt`) |
| :browser-cdp | `android/browser-cdp/src/main/kotlin/ai/eqo/browser/cdp/ChromeCdpClient.kt` | EXTRACT | `ai.eqo.browser.cdp` | imoonkey/closepaw @ 75dae2653f5a6b25d5df51ee7008b0f830de1536 (upstream path: `app/src/main/kotlin/ai/closepaw/browser/cdp/ChromeCdpClient.kt`) |
| :browser-cdp | `android/browser-cdp/src/main/kotlin/ai/eqo/browser/cdp/ChromeCdpCommand.kt` | EXTRACT | `ai.eqo.browser.cdp` | imoonkey/closepaw @ 75dae2653f5a6b25d5df51ee7008b0f830de1536 (upstream path: `app/src/main/kotlin/ai/closepaw/browser/cdp/ChromeCdpCommand.kt`) |
| :browser-cdp | `android/browser-cdp/src/main/kotlin/ai/eqo/browser/cdp/ChromeCdpEventBuffer.kt` | EXTRACT | `ai.eqo.browser.cdp` | imoonkey/closepaw @ 75dae2653f5a6b25d5df51ee7008b0f830de1536 (upstream path: `app/src/main/kotlin/ai/closepaw/browser/cdp/ChromeCdpEventBuffer.kt`) |
| :browser-cdp | `android/browser-cdp/src/main/kotlin/ai/eqo/browser/cdp/ChromeCdpTarget.kt` | EXTRACT | `ai.eqo.browser.cdp` | imoonkey/closepaw @ 75dae2653f5a6b25d5df51ee7008b0f830de1536 (upstream path: `app/src/main/kotlin/ai/closepaw/browser/cdp/ChromeCdpTarget.kt`) |
| :browser-cdp | `android/browser-cdp/src/main/kotlin/ai/eqo/browser/cdp/DevtoolsHttpProtocol.kt` | EXTRACT | `ai.eqo.browser.cdp` | imoonkey/closepaw @ 75dae2653f5a6b25d5df51ee7008b0f830de1536 (upstream path: `app/src/main/kotlin/ai/closepaw/browser/cdp/shizuku/DevtoolsHttpProtocol.kt`; shizuku sub-package dropped) |
| :browser-cdp | `android/browser-cdp/src/test/kotlin/ai/eqo/browser/cdp/FakeCdpConnection.kt` | EXTRACT | `ai.eqo.browser.cdp` | imoonkey/closepaw @ 75dae2653f5a6b25d5df51ee7008b0f830de1536 (upstream path: `app/src/test/kotlin/ai/closepaw/browser/cdp/FakeCdpConnection.kt`; adapted to JUnit) |
| :browser-cdp | `android/browser-cdp/src/main/kotlin/ai/eqo/browser/cdp/CdpConsent.kt` | EQO-NEW | `ai.eqo.browser.cdp` | imbalki/project-eqo (EQO-authored, TASK-010 issue #15) |
| :browser-cdp | `android/browser-cdp/src/main/kotlin/ai/eqo/browser/cdp/CdpSetupError.kt` | EQO-NEW | `ai.eqo.browser.cdp` | imbalki/project-eqo (EQO-authored, TASK-010 issue #15) |
| :browser-cdp | `android/browser-cdp/src/main/kotlin/ai/eqo/browser/cdp/ChromeControl.kt` | EQO-NEW | `ai.eqo.browser.cdp` | imbalki/project-eqo (EQO-authored, TASK-010 issue #15) |
| :browser-cdp | `android/browser-cdp/src/main/kotlin/ai/eqo/browser/cdp/ChromeCdpSetup.kt` | EQO-NEW | `ai.eqo.browser.cdp` | imbalki/project-eqo (EQO-authored, TASK-010 issue #15) |
| :browser-cdp | `android/browser-cdp/src/main/kotlin/ai/eqo/browser/cdp/ChromePageController.kt` | EQO-NEW | `ai.eqo.browser.cdp` | imbalki/project-eqo (EQO-authored, TASK-010 issue #15) |
| :browser-cdp | `android/browser-cdp/src/main/kotlin/ai/eqo/browser/cdp/DevtoolsEndpoint.kt` | EQO-NEW | `ai.eqo.browser.cdp` | imbalki/project-eqo (EQO-authored, TASK-010 issue #15) |
| :browser-cdp | `android/browser-cdp/src/test/kotlin/ai/eqo/browser/cdp/CdpConsentTest.kt` | EQO-NEW | `ai.eqo.browser.cdp` | imbalki/project-eqo (EQO-authored, TASK-010 issue #15) |
| :browser-cdp | `android/browser-cdp/src/test/kotlin/ai/eqo/browser/cdp/ChromeCdpCommandTest.kt` | EQO-NEW | `ai.eqo.browser.cdp` | imbalki/project-eqo (EQO-authored, TASK-010 issue #15) |
| :browser-cdp | `android/browser-cdp/src/test/kotlin/ai/eqo/browser/cdp/ChromeCdpSetupTest.kt` | EQO-NEW | `ai.eqo.browser.cdp` | imbalki/project-eqo (EQO-authored, TASK-010 issue #15) |
| :browser-cdp | `android/browser-cdp/src/test/kotlin/ai/eqo/browser/cdp/ChromePageControllerTest.kt` | EQO-NEW | `ai.eqo.browser.cdp` | imbalki/project-eqo (EQO-authored, TASK-010 issue #15) |
| :browser-cdp | `android/browser-cdp/src/test/kotlin/ai/eqo/browser/cdp/DevtoolsSetupTest.kt` | EQO-NEW | `ai.eqo.browser.cdp` | imbalki/project-eqo (EQO-authored, TASK-010 issue #15) |

## TASK-015 additions (issue #20)

The TASK-015 study-app files (guided onboarding screens, capability readiness model,
recovery catalog, study-flow gates, task screen and ActionLoop wiring, plus their host
tests) are EQO-authored with no upstream counterpart and are recorded as **EQO-NEW**
rows below with provenance `imbalki/project-eqo (EQO-authored, TASK-015 issue #20)`.

| Module | File | Kind | Package | Provenance |
|---|---|---|---|---|
| :app | `android/app/src/main/kotlin/ai/eqo/legal/LegalNoticesActivity.kt` | EQO-NEW | `ai.eqo.legal` | imbalki/project-eqo (EQO-authored, TASK-015 issue #20) |
| :app | `android/app/src/main/kotlin/ai/eqo/onboarding/AccessibilitySetupActivity.kt` | EQO-NEW | `ai.eqo.onboarding` | imbalki/project-eqo (EQO-authored, TASK-015 issue #20) |
| :app | `android/app/src/main/kotlin/ai/eqo/onboarding/ChromeConsentActivity.kt` | EQO-NEW | `ai.eqo.onboarding` | imbalki/project-eqo (EQO-authored, TASK-015 issue #20) |
| :app | `android/app/src/main/kotlin/ai/eqo/onboarding/ModelKeySetupActivity.kt` | EQO-NEW | `ai.eqo.onboarding` | imbalki/project-eqo (EQO-authored, TASK-015 issue #20) |
| :app | `android/app/src/main/kotlin/ai/eqo/onboarding/SetupHubActivity.kt` | EQO-NEW | `ai.eqo.onboarding` | imbalki/project-eqo (EQO-authored, TASK-015 issue #20) |
| :app | `android/app/src/main/kotlin/ai/eqo/onboarding/StudySetup.kt` | EQO-NEW | `ai.eqo.onboarding` | imbalki/project-eqo (EQO-authored, TASK-015 issue #20) |
| :app | `android/app/src/main/kotlin/ai/eqo/onboarding/WirelessAdbSetupActivity.kt` | EQO-NEW | `ai.eqo.onboarding` | imbalki/project-eqo (EQO-authored, TASK-015 issue #20) |
| :app | `android/app/src/main/kotlin/ai/eqo/study/CapabilityReadiness.kt` | EQO-NEW | `ai.eqo.study` | imbalki/project-eqo (EQO-authored, TASK-015 issue #20) |
| :app | `android/app/src/main/kotlin/ai/eqo/study/FailureClass.kt` | EQO-NEW | `ai.eqo.study` | imbalki/project-eqo (EQO-authored, TASK-015 issue #20) |
| :app | `android/app/src/main/kotlin/ai/eqo/study/StudyFlowGate.kt` | EQO-NEW | `ai.eqo.study` | imbalki/project-eqo (EQO-authored, TASK-015 issue #20) |
| :app | `android/app/src/main/kotlin/ai/eqo/study/TaskPresentation.kt` | EQO-NEW | `ai.eqo.study` | imbalki/project-eqo (EQO-authored, TASK-015 issue #20) |
| :app | `android/app/src/main/kotlin/ai/eqo/task/EqoAutomationPort.kt` | EQO-NEW | `ai.eqo.task` | imbalki/project-eqo (EQO-authored, TASK-015 issue #20) |
| :app | `android/app/src/main/kotlin/ai/eqo/task/StudyLoopWiring.kt` | EQO-NEW | `ai.eqo.task` | imbalki/project-eqo (EQO-authored, TASK-015 issue #20) |
| :app | `android/app/src/main/kotlin/ai/eqo/task/StudyTaskController.kt` | EQO-NEW | `ai.eqo.task` | imbalki/project-eqo (EQO-authored, TASK-015 issue #20) |
| :app | `android/app/src/main/kotlin/ai/eqo/task/ConfirmationTouchGuard.kt` | EQO-NEW | `ai.eqo.task` | imbalki/project-eqo (EQO-authored, TASK-015 issue #20) |
| :app | `android/app/src/main/kotlin/ai/eqo/task/ProtectedConfirmationTouches.kt` | EQO-NEW | `ai.eqo.task` | imbalki/project-eqo (EQO-authored, TASK-015 issue #20) |
| :app | `android/app/src/test/kotlin/ai/eqo/task/ConfirmationTouchGuardTest.kt` | EQO-NEW | `ai.eqo.task` | imbalki/project-eqo (EQO-authored, TASK-015 issue #20) |
| :app | `android/app/src/main/kotlin/ai/eqo/task/TaskActivity.kt` | EQO-NEW | `ai.eqo.task` | imbalki/project-eqo (EQO-authored, TASK-015 issue #20) |
| :app | `android/app/src/test/kotlin/ai/eqo/study/CapabilityReadinessTest.kt` | EQO-NEW | `ai.eqo.study` | imbalki/project-eqo (EQO-authored, TASK-015 issue #20) |
| :app | `android/app/src/test/kotlin/ai/eqo/study/RecoveryCatalogTest.kt` | EQO-NEW | `ai.eqo.study` | imbalki/project-eqo (EQO-authored, TASK-015 issue #20) |

## TASK-073 additions (issue #20)

| Module | File | Kind | Package | Provenance |
|---|---|---|---|---|
| :actions-android | `android/actions-android/src/main/java/ai/eqo/actions/impl/SystemActions.kt` | EXTRACT | `ai.eqo.actions.impl` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (actions/SystemActions.kt; CLOSE_APP from actions/AdvancedControlActions.kt) |
| :actions-android | `android/actions-android/src/main/java/ai/eqo/actions/impl/ScreenAnalyzer.kt` | EQO-NEW | `ai.eqo.actions.impl` | imbalki/project-eqo (EQO-authored, TASK-073 issue #20) |
| :actions-android | `android/actions-android/src/main/java/ai/eqo/actions/impl/TextScreenAnalyzer.kt` | EXTRACT | `ai.eqo.actions.impl` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (core/agent/VisionEngine.kt text fallback) |
| :actions-android | `android/actions-android/src/test/java/ai/eqo/actions/impl/SystemActionsTest.kt` | EQO-NEW | `ai.eqo.actions.impl` | imbalki/project-eqo (EQO-authored, TASK-073 issue #20) |
| :app | `android/app/src/test/kotlin/ai/eqo/study/StudyFlowGateTest.kt` | EQO-NEW | `ai.eqo.study` | imbalki/project-eqo (EQO-authored, TASK-015 issue #20) |
| :app | `android/app/src/test/kotlin/ai/eqo/study/TaskPresentationTest.kt` | EQO-NEW | `ai.eqo.study` | imbalki/project-eqo (EQO-authored, TASK-015 issue #20) |
| :app | `android/app/src/test/kotlin/ai/eqo/task/StudyLoopWiringTest.kt` | EQO-NEW | `ai.eqo.task` | imbalki/project-eqo (EQO-authored, TASK-015 issue #20) |
| :actions-android | `android/actions-android/src/test/java/ai/eqo/actions/impl/TextScreenAnalyzerTest.kt` | EQO-NEW | `ai.eqo.actions.impl` | imbalki/project-eqo (EQO-authored, TASK-073 issue #20) |



## TASK-074 executor batch 3 additions (Refs #20)

| Module | File | Kind | Package | Provenance |
|---|---|---|---|---|
| :actions-android | `android/actions-android/src/main/java/ai/eqo/actions/impl/CalendarActions.kt` | EXTRACT | `ai.eqo.actions.impl` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream actions/CalendarActions.kt / RoutineActions.kt; EQO TASK-074 adaptation) |
| :actions-android | `android/actions-android/src/main/java/ai/eqo/actions/impl/ProductivityMemoryActions.kt` | EXTRACT | `ai.eqo.actions.impl` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream actions/CalendarActions.kt / RoutineActions.kt; EQO TASK-074 adaptation) |
| :actions-android | `android/actions-android/src/main/java/ai/eqo/actions/impl/InformationActions.kt` | EXTRACT | `ai.eqo.actions.impl` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream actions/InformationActions.kt; EQO TASK-074 adaptation) |
| :actions-android | `android/actions-android/src/main/java/ai/eqo/actions/impl/ConversationActions.kt` | EXTRACT | `ai.eqo.actions.impl` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream actions/SystemActions.kt / ActionSchema.kt; EQO TASK-074 adaptation) |
| :actions-android | `android/actions-android/src/main/java/ai/eqo/actions/impl/AlarmTimeParser.kt` | EXTRACT | `ai.eqo.actions.impl` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (upstream actions/CalendarActions.kt; EQO TASK-074 adaptation) |
| :actions-android | `android/actions-android/src/main/java/ai/eqo/actions/impl/ProductivityDependencies.kt` | EQO-NEW | `ai.eqo.actions.impl` | imbalki/project-eqo (EQO-authored, TASK-074 issue #20) |
| :actions-android | `android/actions-android/src/main/java/ai/eqo/actions/impl/InformationHttp.kt` | EQO-NEW | `ai.eqo.actions.impl` | imbalki/project-eqo (EQO-authored, TASK-074 issue #20) |
| :actions-android | `android/actions-android/src/test/java/ai/eqo/actions/impl/ProductivityInformationTest.kt` | EQO-NEW | `ai.eqo.actions.impl` | imbalki/project-eqo (EQO-authored, TASK-074 issue #20) |

## TASK-078 executor batch 2 additions (Refs #20)

| Module | File | Kind | Package | Provenance |
|---|---|---|---|---|
| :core-llm | `android/core-llm/src/main/java/ai/eqo/data/db/entities/MacroEntity.kt` | EXTRACT | `ai.eqo.data.db.entities` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (data/db/entities/MacroEntity.kt; EQO TASK-078 port) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/data/db/entities/HabitEventEntity.kt` | EXTRACT | `ai.eqo.data.db.entities` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (data/db/entities/HabitEventEntity.kt; EQO TASK-078 port) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/data/db/entities/HabitRoutineEntity.kt` | EXTRACT | `ai.eqo.data.db.entities` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (data/db/entities/HabitRoutineEntity.kt; EQO TASK-078 port) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/data/db/dao/MacroDao.kt` | EXTRACT | `ai.eqo.data.db.dao` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (data/db/dao/MacroDao.kt; EQO TASK-078 port) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/data/db/dao/HabitDao.kt` | EXTRACT | `ai.eqo.data.db.dao` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (data/db/dao/HabitDao.kt; EQO TASK-078 port) |
| :core-llm | `android/core-llm/src/main/java/ai/eqo/data/db/EqoDatabase.kt` | EXTRACT | `ai.eqo.data.db` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (data/db/OpenDroidDatabase.kt; four tables only, version 1; EQO TASK-078 adaptation) |
| :actions-android | `android/actions-android/src/main/java/ai/eqo/actions/impl/NotificationActions.kt` | EXTRACT | `ai.eqo.actions.impl` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (actions/NotificationActions.kt; EQO TASK-078 adaptation) |
| :actions-android | `android/actions-android/src/main/java/ai/eqo/actions/impl/MacroActions.kt` | EXTRACT | `ai.eqo.actions.impl` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (actions/MacroActions.kt; EQO TASK-078 adaptation) |
| :actions-android | `android/actions-android/src/main/java/ai/eqo/actions/impl/RoutineActions.kt` | EXTRACT | `ai.eqo.actions.impl` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (actions/RoutineActions.kt; EQO TASK-078 adaptation) |
| :actions-android | `android/actions-android/src/main/java/ai/eqo/actions/impl/HabitRoutineEngine.kt` | EXTRACT | `ai.eqo.actions.impl` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (core/routine/HabitRoutineEngine.kt; EQO TASK-078 adaptation) |
| :actions-android | `android/actions-android/src/main/java/ai/eqo/actions/impl/RoutineDetection.kt` | EXTRACT | `ai.eqo.actions.impl` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (core/routine/HabitRoutineEngine.kt, pattern detection; EQO TASK-078 adaptation) |
| :actions-android | `android/actions-android/src/main/java/ai/eqo/actions/impl/RoutineTemplates.kt` | EXTRACT | `ai.eqo.actions.impl` | yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51 (core/routine/HabitRoutineEngine.kt, step templates; EQO TASK-078 adaptation) |
| :actions-android | `android/actions-android/src/main/java/ai/eqo/actions/impl/AutomationData.kt` | EQO-NEW | `ai.eqo.actions.impl` | imbalki/project-eqo (EQO-authored, TASK-078 issue #20) |
| :actions-android | `android/actions-android/src/main/java/ai/eqo/actions/impl/NestedActionRunner.kt` | EQO-NEW | `ai.eqo.actions.impl` | imbalki/project-eqo (EQO-authored, TASK-078 issue #20) |
| :actions-android | `android/actions-android/src/test/java/ai/eqo/actions/impl/AutomationExecutorsTest.kt` | EQO-NEW | `ai.eqo.actions.impl` | imbalki/project-eqo (EQO-authored, TASK-078 issue #20) |
| :actions-android | `android/actions-android/src/test/java/ai/eqo/actions/impl/RoutineDetectionTest.kt` | EQO-NEW | `ai.eqo.actions.impl` | imbalki/project-eqo (EQO-authored, TASK-078 issue #20) |


## TASK-080 additions (Refs #20)

| Module | File | Kind | Package | Provenance |
|---|---|---|---|---|
| :adb-pairing | `android/adb-pairing/src/main/kotlin/ai/eqo/adb/pairing/ServerPin.kt` | EQO-NEW | `ai.eqo.adb.pairing` | imbalki/project-eqo (EQO-authored, TASK-080 issue #20) |
| :adb-pairing | `android/adb-pairing/src/main/kotlin/ai/eqo/adb/pairing/AdbShellLauncher.kt` | EQO-NEW | `ai.eqo.adb.pairing` | imbalki/project-eqo (EQO-authored, TASK-080 issue #20) |
| :adb-pairing | `android/adb-pairing/src/main/kotlin/ai/eqo/adb/pairing/WirelessLinkState.kt` | EQO-NEW | `ai.eqo.adb.pairing` | imbalki/project-eqo (EQO-authored, TASK-080 issue #20) |
| :adb-pairing | `android/adb-pairing/src/test/kotlin/ai/eqo/adb/pairing/ServerPinTest.kt` | EQO-NEW | `ai.eqo.adb.pairing` | imbalki/project-eqo (EQO-authored, TASK-080 issue #20) |
| :adb-pairing | `android/adb-pairing/src/test/kotlin/ai/eqo/adb/pairing/WirelessReconnectTest.kt` | EQO-NEW | `ai.eqo.adb.pairing` | imbalki/project-eqo (EQO-authored, TASK-080 issue #20) |
| :app | `android/app/src/main/kotlin/ai/eqo/onboarding/StudyHelperHooks.kt` | EQO-NEW | `ai.eqo.onboarding` | imbalki/project-eqo (EQO-authored, TASK-080 issue #20) |
| :app | `android/app/src/test/kotlin/ai/eqo/onboarding/WirelessProbeTest.kt` | EQO-NEW | `ai.eqo.onboarding` | imbalki/project-eqo (EQO-authored, TASK-080 issue #20) |
| :adb-pairing | `android/adb-pairing/src/main/kotlin/ai/eqo/adb/pairing/ServerEnrollmentStore.kt` | EQO-NEW | `ai.eqo.adb.pairing` | imbalki/project-eqo (EQO-authored, TASK-080 issue #20) |
| :adb-pairing | `android/adb-pairing/src/main/kotlin/ai/eqo/adb/pairing/ConnectKeyEnrollment.kt` | EQO-NEW | `ai.eqo.adb.pairing` | imbalki/project-eqo (EQO-authored, TASK-080 issue #20, PR #77 CONNECT-plane correction) |
| :adb-pairing | `android/adb-pairing/src/test/kotlin/ai/eqo/adb/pairing/ConnectKeyEnrollmentTest.kt` | EQO-NEW | `ai.eqo.adb.pairing` | imbalki/project-eqo (EQO-authored, TASK-080 issue #20, PR #77 CONNECT-plane correction) |

| :core-agent | `android/core-agent/src/test/java/ai/eqo/core/agent/ContactResolverTest.kt` | NEW | `ai.eqo.core.agent` | EQO contacts task t_212ea16c: fake Contacts provider safety and privacy tests |
| :actions-android | `android/actions-android/src/test/java/ai/eqo/actions/impl/ContactRecipientsTest.kt` | NEW | `ai.eqo.actions.impl` | EQO contacts task t_212ea16c: five-action fake recipient and approval regressions |
## Helper authorization follow-up to PR #77 (Refs #20)

| Module | File | Kind | Package | Provenance |
|---|---|---|---|---|
| :app | `android/app/src/main/kotlin/ai/eqo/onboarding/HelperAuthorization.kt` | EQO-NEW | `ai.eqo.onboarding` | imbalki/project-eqo (EQO-authored, helper consent follow-up to PR #77) |
| :app | `android/app/src/main/kotlin/ai/eqo/onboarding/HelperPermissionPrompt.kt` | EQO-NEW | `ai.eqo.onboarding` | imbalki/project-eqo (EQO-authored, helper consent follow-up to PR #77) |
| :app | `android/app/src/test/kotlin/ai/eqo/onboarding/HelperAuthorizationTest.kt` | EQO-NEW | `ai.eqo.onboarding` | imbalki/project-eqo (EQO-authored, helper consent follow-up to PR #77) |
| :app | `android/app/src/test/kotlin/ai/eqo/onboarding/HelperPermissionPromptTest.kt` | EQO-NEW | `ai.eqo.onboarding` | imbalki/project-eqo (EQO-authored, helper consent follow-up to PR #77) |
| :actions-android | `android/actions-android/src/main/java/ai/eqo/actions/impl/ShareActions.kt` | EQO-NEW | `ai.eqo.actions.impl` | imbalki/project-eqo (EQO-authored, share contact / share location actions) |
| :actions-android | `android/actions-android/src/test/java/ai/eqo/actions/impl/ShareActionsTest.kt` | EQO-NEW | `ai.eqo.actions.impl` | imbalki/project-eqo (EQO-authored, share contact / share location actions) |
| :actions-android | `android/actions-android/src/main/java/ai/eqo/actions/impl/AttachmentShare.kt` | EQO-NEW | `ai.eqo.actions.impl` | imbalki/project-eqo (EQO-authored, files-attachments: shared files, attachments, screenshots) |
| :actions-android | `android/actions-android/src/main/java/ai/eqo/actions/impl/EqoSharedFileProvider.kt` | EQO-NEW | `ai.eqo.actions.impl` | imbalki/project-eqo (EQO-authored, files-attachments: shared files, attachments, screenshots) |
| :actions-android | `android/actions-android/src/main/java/ai/eqo/actions/impl/FileActions.kt` | EQO-NEW | `ai.eqo.actions.impl` | imbalki/project-eqo (EQO-authored, files-attachments: shared files, attachments, screenshots) |
| :actions-android | `android/actions-android/src/main/java/ai/eqo/actions/impl/FileShareIntents.kt` | EQO-NEW | `ai.eqo.actions.impl` | imbalki/project-eqo (EQO-authored, files-attachments: shared files, attachments, screenshots) |
| :actions-android | `android/actions-android/src/main/java/ai/eqo/actions/impl/LastScreenshotStore.kt` | EQO-NEW | `ai.eqo.actions.impl` | imbalki/project-eqo (EQO-authored, files-attachments: shared files, attachments, screenshots) |
| :actions-android | `android/actions-android/src/main/java/ai/eqo/actions/impl/ScreenshotCapture.kt` | EQO-NEW | `ai.eqo.actions.impl` | imbalki/project-eqo (EQO-authored, files-attachments: shared files, attachments, screenshots) |
| :actions-android | `android/actions-android/src/main/java/ai/eqo/actions/impl/ShareStaging.kt` | EQO-NEW | `ai.eqo.actions.impl` | imbalki/project-eqo (EQO-authored, files-attachments: shared files, attachments, screenshots) |
| :actions-android | `android/actions-android/src/main/java/ai/eqo/actions/impl/SharedFileBrowser.kt` | EQO-NEW | `ai.eqo.actions.impl` | imbalki/project-eqo (EQO-authored, files-attachments: shared files, attachments, screenshots) |
| :actions-android | `android/actions-android/src/main/java/ai/eqo/actions/impl/SharedStorageLayout.kt` | EQO-NEW | `ai.eqo.actions.impl` | imbalki/project-eqo (EQO-authored, files-attachments: shared files, attachments, screenshots) |
| :actions-android | `android/actions-android/src/main/java/ai/eqo/actions/impl/SharedStorageServices.kt` | EQO-NEW | `ai.eqo.actions.impl` | imbalki/project-eqo (EQO-authored, files-attachments: shared files, attachments, screenshots) |
| :actions-android | `android/actions-android/src/test/java/ai/eqo/actions/impl/FileFeaturesRegistryTest.kt` | EQO-NEW | `ai.eqo.actions.impl` | imbalki/project-eqo (EQO-authored, files-attachments: shared files, attachments, screenshots) |
| :actions-android | `android/actions-android/src/test/java/ai/eqo/actions/impl/SharedStorageTest.kt` | EQO-NEW | `ai.eqo.actions.impl` | imbalki/project-eqo (EQO-authored, files-attachments: shared files, attachments, screenshots) |
| :app | `android/app/src/main/kotlin/ai/eqo/onboarding/AllFilesAccess.kt` | EQO-NEW | `ai.eqo.onboarding` | imbalki/project-eqo (EQO-authored, files-attachments: shared files, attachments, screenshots) |
| :app | `android/app/src/test/kotlin/ai/eqo/onboarding/AllFilesAccessTest.kt` | EQO-NEW | `ai.eqo.onboarding` | imbalki/project-eqo (EQO-authored, files-attachments: shared files, attachments, screenshots) |
| :core-agent | `android/core-agent/src/main/java/ai/eqo/core/agent/AttachmentSpec.kt` | EQO-NEW | `ai.eqo.core.agent` | imbalki/project-eqo (EQO-authored, files-attachments: shared files, attachments, screenshots) |
| :core-agent | `android/core-agent/src/test/java/ai/eqo/core/agent/AttachmentSpecTest.kt` | EQO-NEW | `ai.eqo.core.agent` | imbalki/project-eqo (EQO-authored, files-attachments: shared files, attachments, screenshots) |
| :core-agent | `android/core-agent/src/main/java/ai/eqo/core/agent/AttachmentSearch.kt` | EQO-NEW | `ai.eqo.core.agent` | imbalki/project-eqo (EQO-authored, Files v2 run-time attachment resolution, t_ecfe91de) |
| :actions-android | `android/actions-android/src/main/java/ai/eqo/actions/impl/AttachmentFileSearch.kt` | EQO-NEW | `ai.eqo.actions.impl` | imbalki/project-eqo (EQO-authored, Files v2 run-time attachment resolution, t_ecfe91de) |
| :actions-android | `android/actions-android/src/main/java/ai/eqo/actions/impl/AttachmentSelection.kt` | EQO-NEW | `ai.eqo.actions.impl` | imbalki/project-eqo (EQO-authored, Files v2 run-time attachment resolution, t_ecfe91de) |
| :actions-android | `android/actions-android/src/test/java/ai/eqo/actions/impl/AttachmentFileSearchTest.kt` | EQO-NEW | `ai.eqo.actions.impl` | imbalki/project-eqo (EQO-authored, Files v2 run-time attachment resolution, t_ecfe91de) |
| :app | `android/app/src/main/kotlin/ai/eqo/task/TaskAttachmentSelection.kt` | EQO-NEW | `ai.eqo.task` | imbalki/project-eqo (EQO-authored, Files v2 run-time attachment resolution, t_ecfe91de) |
| :app | `android/app/src/test/kotlin/ai/eqo/task/TaskAttachmentSelectionTest.kt` | EQO-NEW | `ai.eqo.task` | imbalki/project-eqo (EQO-authored, Files v2 run-time attachment resolution, t_ecfe91de) |
| :actions-android | `android/actions-android/src/main/java/ai/eqo/actions/impl/SharedFolderAliases.kt` | EQO-NEW | `ai.eqo.actions.impl` | imbalki/project-eqo (EQO-authored, Files v2 OEM alias data, t_ecfe91de) |
| :actions-android | `android/actions-android/src/main/java/ai/eqo/actions/impl/SharedStorageCatalog.kt` | EQO-NEW | `ai.eqo.actions.impl` | imbalki/project-eqo (EQO-authored, Files v2 per-phone discovery, t_ecfe91de) |
| :actions-android | `android/actions-android/src/main/java/ai/eqo/actions/impl/AndroidSharedMediaSource.kt` | EQO-NEW | `ai.eqo.actions.impl` | imbalki/project-eqo (EQO-authored, Files v2 MediaStore metadata, t_ecfe91de) |
| :actions-android | `android/actions-android/src/test/java/ai/eqo/actions/impl/SharedStorageCatalogTest.kt` | EQO-NEW | `ai.eqo.actions.impl` | imbalki/project-eqo (EQO-authored, Files v2 Realme/Samsung/Xiaomi fake layouts, t_ecfe91de) |
