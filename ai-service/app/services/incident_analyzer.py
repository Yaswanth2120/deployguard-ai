from app.schemas import IncidentAnalysisRequest, IncidentAnalysisResponse


def analyze_incident(request: IncidentAnalysisRequest) -> IncidentAnalysisResponse:
    risk_level = request.riskLevel

    if risk_level == "HIGH":
        return IncidentAnalysisResponse(
            summary="High deployment risk detected. The deployment should be reviewed before further rollout.",
            likelyRootCause="Mock analysis indicates severe risk signals from CI/CD results, logs, or deployment context.",
            evidence=[],
            recommendedActions=[
                "Review failed CI runs and recent error logs.",
                "Pause or roll back the deployment if user impact is confirmed.",
            ],
            severity="HIGH",
            confidence="MEDIUM",
        )

    if risk_level == "MEDIUM":
        return IncidentAnalysisResponse(
            summary="Medium deployment risk detected. The deployment has warning signals that need investigation.",
            likelyRootCause="Mock analysis indicates moderate risk signals from CI/CD results, logs, or deployment context.",
            evidence=[],
            recommendedActions=[
                "Inspect related CI runs and deployment logs.",
                "Monitor service health before expanding the rollout.",
            ],
            severity="MEDIUM",
            confidence="MEDIUM",
        )

    return IncidentAnalysisResponse(
        summary="Low deployment risk detected. No severe incident indicators were found in the provided context.",
        likelyRootCause="Mock analysis indicates no strong failure pattern in the deployment context.",
        evidence=[],
        recommendedActions=[
            "Continue monitoring normal deployment metrics.",
        ],
        severity="LOW",
        confidence="LOW",
    )
