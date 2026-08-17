---
status: accepted
---

# Start as a modular monolith

SmartCareOS starts as one Spring Boot deployment with explicit domain modules. The source material supports Spring Cloud but does not establish the scale, team topology, or independent release needs that would justify distributed services today; preserving context boundaries now lets the project split selected modules later without paying the operational cost of premature microservices.

