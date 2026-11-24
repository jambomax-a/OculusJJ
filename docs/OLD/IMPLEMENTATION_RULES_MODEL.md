# AI Model Selection Rules for JJ Dicom Viewer

## Overview
This document specifies the rules for handling AI model selection when the MODEL parameter is set to "Auto" in JJ Dicom Viewer and related AI agent applications.

## Requirement: Display Used Model When MODEL="Auto"

### Mandatory Rule
When the MODEL parameter is set to "Auto":
1. **The application must automatically select an appropriate model**
2. **The selected model MUST be explicitly displayed to the user**
3. **Display format**: `[AI Model] Using model: <model_name>`

### Why This Rule Exists
- **Transparency**: Users need to know which model is being used
- **Reproducibility**: Essential for debugging and replicating results
- **Traceability**: Audit logs can track which model was used
- **System Monitoring**: Operations teams can verify model selection

## Implementation Requirements

### 1. Logging Output
```
[AI Model] Using model: Claude 3.5 Sonnet
[AI Model] Using model: Claude Haiku 4.5
```

### 2. Console Output
The selected model should be printed to the console at startup:
```
System.out.println("[AI Model] Using model: " + selectedModel);
```

### 3. Application Log
Log the selection using the logger:
```java
logger.info("[AI Model] Using model: {}", selectedModel);
```

## Model Selection Strategy (Auto Mode)

### Current Implementation
The application uses the following strategy for Auto mode:

| Use Case | Recommended Model | Reason |
|----------|------------------|--------|
| Complex image analysis | Claude 3.5 Sonnet | High reasoning capability |
| DICOM processing | Claude 3.5 Sonnet | Medical image understanding |
| Text processing | Claude Haiku 4.5 | Speed and efficiency |
| Agent development | Claude 3.5 Sonnet | Complex task coordination |

### Selection Factors
- Task complexity
- Performance requirements
- Available models in the system
- User preferences (if configurable)

## Validation Checklist

Before deploying applications with Auto MODEL:
- [ ] Model selection logic is implemented
- [ ] Selected model is logged at startup
- [ ] Log message format matches `[AI Model] Using model: XXX`
- [ ] Console output displays the selected model
- [ ] Multiple runs select consistent models (if deterministic)
- [ ] Error handling for unavailable models is implemented

## Troubleshooting

### Issue: Selected model is not displayed
**Solution**: Verify that the logging and console output statements are in the initialization path.

### Issue: Model selection is non-deterministic
**Solution**: Implement a deterministic selection algorithm or add user configuration options.

### Issue: Model is displayed but selection is incorrect
**Solution**: Review and test the model selection logic against expected criteria.

## Configuration

### Environment Variables
Optionally, the following environment variables can be used:
- `AI_MODEL_STRATEGY`: Selection strategy (auto/manual)
- `AI_MODEL_PREFERENCE`: Preferred model (if multiple are available)

### Configuration File
Models can be configured in `config.properties`:
```properties
ai.model.strategy=auto
ai.model.preference=claude-3-5-sonnet
```

## References
- [AI_MODEL_RULES.md](./AI_MODEL_RULES.md) - Detailed rules in Japanese
- [AI Toolkit Documentation](./docs/) - Related documentation

---

**Document Version**: 1.0  
**Last Updated**: 2026-01-06  
**Applicable to**: JJ Dicom Viewer, AI Agent Framework
