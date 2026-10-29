# Plugin development and usage guide

## What a plugin is

A plugin is a JSON file that declares one or more tools. The AI decides when to call a tool and fills in its parameters, DevCodex sends an HTTP request using the template you wrote, and then hands the response back to the AI.

Good fits: weather lookups, Home Assistant, n8n workflows, your own APIs, internal systems.

## Plugins vs. skills

| Type | Role | Format |
| --- | --- | --- |
| Plugin | Gives the AI a hand: calls an external HTTP endpoint | JSON |
| Skill | Gives the AI knowledge: methods and process descriptions | SKILL.md |

They work together: a skill explains how to do something, a plugin provides the tool to call.

## Quick start

Copy the JSON below, open the Plugins page, tap the button in the lower right, and choose paste-to-install.

```json
{
  "id": "weather-demo",
  "name": "Weather",
  "version": "1.0.0",
  "author": "Your name",
  "description": "Look up city weather",
  "tools": [
    {
      "name": "get_weather",
      "description": "Call this when the user asks about the weather in a city",
      "parameters": "{\"type\":\"object\",\"properties\":{\"city\":{\"type\":\"string\",\"description\":\"English city name, e.g. Beijing\"}},\"required\":[\"city\"]}",
      "endpoint": {
        "method": "GET",
        "url": "https://wttr.in/{{city}}?format=3"
      }
    }
  ]
}
```

After installing, say "what is the weather in Beijing" in a chat and the AI will call get_weather automatically.

## Field reference

### Plugin level

| Field | Required | Description |
| --- | --- | --- |
| id | Yes | Unique identifier; installing the same id again overwrites the old version |
| name | Yes | Display name |
| version | No | Version number, defaults to 1.0.0 |
| author | No | Author |
| description | No | Plugin description |
| enabled | No | Whether it is enabled, defaults to true |
| tools | Yes | Tool list, at least 1 |

### Tool level (each entry in tools)

| Field | Required | Description |
| --- | --- | --- |
| name | Yes | Tool name, in English, no spaces |
| description | No | State clearly when to use it; the AI relies on this to decide |
| parameters | No | JSON Schema as a string, defaults to {} |
| endpoint | Yes | HTTP request configuration |

### Endpoint level (endpoint)

| Field | Required | Description |
| --- | --- | --- |
| url | Yes | Request URL, supports {{parameter}} |
| method | No | GET, POST, PUT, DELETE; defaults to POST |
| headers | No | Request headers; values support {{parameter}} |
| bodyTemplate | No | Request body template, used for POST and PUT |
| resultPath | No | Path to extract the result from the JSON response |

## Parameter syntax

parameters is a string containing JSON Schema; inner double quotes must be escaped as `\"`:

```json
"{\"type\":\"object\",\"properties\":{\"city\":{\"type\":\"string\",\"description\":\"City name\"}},\"required\":[\"city\"]}"
```

Write a description for every parameter; the AI relies on it to fill in the right value. If parameters fails to parse, the tool degrades to having no parameters.

## Template rules

- {{parameter}} is replaced verbatim with the value the AI filled in, with no URL encoding.
- Spaces in the URL are converted to %20 automatically; other special characters require the parameter value itself to be valid.
- Parameter names must exactly match the names inside {{}}; unmatched placeholders are kept as-is.
- GET and DELETE do not send a body; parameters can only be passed via placeholders in the URL and headers, and parameters without placeholders are dropped.
- For other methods (POST, PUT, PATCH, etc.), if no bodyTemplate is set the body is the JSON of the parameters the AI filled in; if one is set, the body is sent from that template.
- The body Content-Type is always application/json.

An example with headers and a body:

```json
"endpoint": {
  "method": "POST",
  "url": "https://api.example.com/v1/notes",
  "headers": { "Authorization": "Bearer YOUR_KEY" },
  "bodyTemplate": "{\"title\": \"{{title}}\", \"text\": \"{{text}}\"}"
}
```

## Result extraction: resultPath

- Empty: returns the full response, keeping at most the first 8000 characters.
- data.items: walks nested JSON object fields by dot notation.
- Only object fields are supported; array indexes are not.
- If the path is missing along the way or the response is not JSON, the full response is returned (also capped at 8000 characters).
- A matched value is returned as JSON text: string fields come with double quotes, objects and arrays return their JSON content, also capped at 8000 characters.
- On failure the AI receives the HTTP status code plus the first 500 characters of the response, or an error message starting with ERROR.

## Install and validation

Three ways are supported: install from a file, download from a URL, paste JSON. On install it checks:

1. id and name must not be empty.
2. At least one tool must be declared.
3. Every tool must have name and endpoint.url.
4. Tool names must not contain spaces.

Avoid duplicate tool names within one plugin, and avoid clashes between plugins, otherwise the AI cannot tell them apart.

## Debugging flow

1. Save the JSON and install it; confirm the plugin appears on the Plugins page with its switch on.
2. State clearly what you want in a chat, e.g. "use get_weather to check the weather in Beijing".
3. Expand the tool card to inspect the parameters the AI filled in and the returned content.
4. On HTTP 4xx, check headers and parameters; on ERROR, check the URL and network.
5. Edit the JSON and reinstall with the same id to overwrite.

## Writing tips

1. In description, state clearly when to use it; do not just describe the API.
2. Put API keys in headers, never inside the URL.
3. Set resultPath for large responses to reduce what the AI reads.
4. One tool should do one thing, with as few parameters as possible.
5. Plugin JSON is stored in plain text in the app's private directory; do not share files containing real keys.

## Skill (SKILL.md) format

A skill is a Markdown file that begins with frontmatter holding its name and summary:

```markdown
---
name: translate
description: Chinese-English translation, use when the user asks to translate
---
Translate the user's content into Chinese, or into English if it is already Chinese, and output only the translation.
```

- name is required; description is recommended.
- description stays in the system prompt; the body is read by the AI via skill_read when needed.
- Install skills from the Skills entry in Settings, or import them from a GitHub repository.
