# 플러그인 개발 및 사용 가이드

## 플러그인이란

플러그인은 하나 이상의 도구를 선언하는 JSON 파일입니다. AI가 언제 도구를 호출할지 판단하고 매개변수를 채우면, DevCodex가 작성한 템플릿대로 HTTP 요청을 보내고 응답을 AI에게 돌려줍니다.

적합한 용도: 날씨 조회, Home Assistant, n8n 워크플로, 자체 API, 내부 시스템.

## 플러그인과 스킬의 차이

| 유형 | 역할 | 형식 |
| --- | --- | --- |
| 플러그인 | AI에 손을 하나 더: 외부 HTTP 엔드포인트 호출 | JSON |
| 스킬 | AI에 지식을 하나 더: 방법과 절차 설명 | SKILL.md |

둘은 함께 쓸 수 있습니다. 스킬은 하는 방법을 설명하고, 플러그인은 호출할 도구를 제공합니다.

## 빠른 시작

아래 JSON을 복사한 뒤 플러그인 페이지 오른쪽 아래 버튼을 눌러 붙여넣기 설치를 선택하세요.

```json
{
  "id": "weather-demo",
  "name": "날씨 조회",
  "version": "1.0.0",
  "author": "당신의 이름",
  "description": "도시 날씨 조회",
  "tools": [
    {
      "name": "get_weather",
      "description": "사용자가 특정 도시의 날씨를 물을 때 호출",
      "parameters": "{\"type\":\"object\",\"properties\":{\"city\":{\"type\":\"string\",\"description\":\"영문 도시명, 예: Beijing\"}},\"required\":[\"city\"]}",
      "endpoint": {
        "method": "GET",
        "url": "https://wttr.in/{{city}}?format=3"
      }
    }
  ]
}
```

설치 후 대화에서 "Beijing 날씨 알려줘"라고 하면 AI가 자동으로 get_weather를 호출합니다.

## 필드 설명

### 플러그인 계층

| 필드 | 필수 | 설명 |
| --- | --- | --- |
| id | 예 | 고유 식별자. 같은 id로 다시 설치하면 이전 버전을 덮어씁니다 |
| name | 예 | 표시 이름 |
| version | 아니오 | 버전 번호, 기본값 1.0.0 |
| author | 아니오 | 작성자 |
| description | 아니오 | 플러그인 설명 |
| enabled | 아니오 | 사용 여부, 기본값 true |
| tools | 예 | 도구 목록, 최소 1개 |

### 도구 계층(tools의 각 항목)

| 필드 | 필수 | 설명 |
| --- | --- | --- |
| name | 예 | 도구 이름, 영문, 공백 불가 |
| description | 아니오 | 언제 써야 하는지 명확히 작성. AI가 이를 보고 호출 여부를 결정 |
| parameters | 아니오 | JSON Schema의 문자열 형식, 기본값 {} |
| endpoint | 예 | HTTP 요청 설정 |

### 엔드포인트 계층(endpoint)

| 필드 | 필수 | 설명 |
| --- | --- | --- |
| url | 예 | 요청 주소, {{매개변수명}} 지원 |
| method | 아니오 | GET, POST, PUT, DELETE, 기본값 POST |
| headers | 아니오 | 요청 헤더, 값에 {{매개변수명}} 지원 |
| bodyTemplate | 아니오 | 요청 본문 템플릿, POST와 PUT에서 사용 |
| resultPath | 아니오 | JSON 응답에서 결과를 추출할 경로 |

## 매개변수 표기

parameters는 JSON Schema를 담은 문자열이며, 내부 큰따옴표는 `\"`로 이스케이프합니다:

```json
"{\"type\":\"object\",\"properties\":{\"city\":{\"type\":\"string\",\"description\":\"도시명\"}},\"required\":[\"city\"]}"
```

모든 매개변수에 description을 작성하세요. AI가 이를 보고 올바른 값을 채웁니다. parameters 파싱에 실패하면 도구는 매개변수가 없는 형태로 퇴화합니다.

## 템플릿 규칙

- {{매개변수명}}은 AI가 입력한 값으로 그대로 치환되며 URL 인코딩을 하지 않습니다.
- URL의 공백은 자동으로 %20으로 변환되며, 그 밖의 특수 문자는 매개변수 값 자체가 유효해야 합니다.
- 매개변수 이름과 {{}} 안의 이름은 정확히 일치해야 하며, 일치하지 않는 자리표시자는 그대로 남습니다.
- GET과 DELETE는 본문을 보내지 않습니다. 매개변수는 URL과 headers의 자리표시자로만 전달할 수 있고, 자리표시자가 없는 매개변수는 버려집니다.
- 그 밖의 메서드(POST, PUT, PATCH 등)에 bodyTemplate이 없으면 본문은 AI가 입력한 매개변수 JSON이 되고, 있으면 템플릿대로 보냅니다.
- 본문의 Content-Type은 항상 application/json입니다.

헤더와 본문이 있는 예:

```json
"endpoint": {
  "method": "POST",
  "url": "https://api.example.com/v1/notes",
  "headers": { "Authorization": "Bearer YOUR_KEY" },
  "bodyTemplate": "{\"title\": \"{{title}}\", \"text\": \"{{text}}\"}"
}
```

## 결과 추출 resultPath

- 비움: 전체 응답을 반환하며 앞 8000자까지 유지합니다.
- data.items: 점 표기법으로 JSON 객체 필드를 따라 내려갑니다.
- 객체 필드만 지원하며 배열 인덱스는 지원하지 않습니다.
- 도중에 경로가 없거나 응답이 JSON이 아니면 전체 응답을 반환합니다(역시 8000자까지).
- 일치한 값은 JSON 텍스트로 반환됩니다. 문자열 필드는 큰따옴표가 붙고, 객체와 배열은 그 JSON 내용을 반환하며 역시 8000자까지입니다.
- 실패하면 AI는 HTTP 상태 코드와 응답 앞 500자, 또는 ERROR로 시작하는 예외 메시지를 받습니다.

## 설치와 검증

세 가지 방식을 지원합니다: 파일에서 설치, URL로 다운로드, JSON 붙여넣기. 설치 시 다음을 검사합니다:

1. id와 name은 비어 있을 수 없습니다.
2. 도구를 최소 1개 선언해야 합니다.
3. 모든 도구에 name과 endpoint.url이 있어야 합니다.
4. 도구 이름에 공백을 쓸 수 없습니다.

같은 플러그인 안에서 도구 이름이 중복되지 않게 하고, 플러그인 사이에서도 충돌을 피하세요. 그렇지 않으면 AI가 구분할 수 없습니다.

## 디버깅 흐름

1. JSON을 저장하고 설치한 뒤, 플러그인 페이지에 표시되고 스위치가 켜져 있는지 확인합니다.
2. 대화에서 하려는 일을 분명히 말합니다. 예: "get_weather로 Beijing 날씨 조회".
3. 도구 카드를 펼쳐 AI가 입력한 매개변수와 반환 내용을 확인합니다.
4. HTTP 4xx가 오면 headers와 매개변수를, ERROR가 오면 URL과 네트워크를 확인합니다.
5. JSON을 수정한 뒤 같은 id로 다시 설치하면 덮어쓸 수 있습니다.

## 작성 팁

1. description에는 언제 쓰는지 분명히 쓰고, API가 무엇인지만 쓰지 마세요.
2. API Key는 headers에 넣고 URL에 붙이지 마세요.
3. 응답이 크면 resultPath를 설정해 AI가 읽는 양을 줄이세요.
4. 도구 하나는 한 가지 일만 하게 하고, 매개변수는 가능한 한 적게 두세요.
5. 플러그인 JSON은 앱 전용 디렉터리에 평문으로 저장됩니다. 실제 키가 담긴 파일은 공유하지 마세요.

## 스킬(SKILL.md) 형식

스킬은 Markdown 파일이며, 맨 앞의 frontmatter에 이름과 소개를 적습니다:

```markdown
---
name: translate
description: 중영 상호 번역, 사용자가 번역을 요청할 때 사용
---
사용자 내용을 중국어로 번역하고, 이미 중국어면 영어로 번역해 번역문만 출력합니다.
```

- name은 필수, description은 작성 권장입니다.
- description은 시스템 프롬프트에 상주하며, 본문은 필요할 때 AI가 skill_read로 읽습니다.
- 설정 페이지의 스킬 입구에서 설치하거나 GitHub 저장소에서 가져올 수 있습니다.
