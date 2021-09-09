---
title: Notify Service v1
language_tabs:
  - shell: Shell
  - http: HTTP
  - javascript: JavaScript
  - ruby: Ruby
  - python: Python
  - php: PHP
  - java: Java
  - go: Go
toc_footers: []
includes: []
search: true
highlight_theme: darkula
headingLevel: 2

---

<!-- Generator: Widdershins v4.0.1 -->

<h1 id="notify-service">Notify Service v1</h1>

> Scroll down for code samples, example requests and responses. Select a language for code samples from the tabs above or the mobile navigation menu.

Service for contacting respondents via Gov Notify SMS messages

Base URLs:

* <a href="http://localhost:64117/">http://localhost:64117/</a>

<h1 id="notify-service-sms-fulfilment-endpoint">sms-fulfilment-endpoint</h1>

## smsFulfilment

<a id="opIdsmsFulfilment"></a>

> Code samples

```shell
# You can also use wget
curl -X POST http://localhost:64117/sms-fulfilment \
  -H 'Content-Type: application/json' \
  -H 'Accept: */*'

```

```http
POST http://localhost:64117/sms-fulfilment HTTP/1.1
Host: localhost:64117
Content-Type: application/json
Accept: */*

```

```javascript
const inputBody = '{
  "header": {
    "source": "Survey Enquiry Line API",
    "channel": "RH",
    "correlationId": "48fb4cd3-2ef6-4479-bea1-7c92721b988c",
    "originatingUser": "fred.bloggs@ons.gov.uk"
  },
  "payload": {
    "smsFulfilment": {
      "caseId": "af51d69f-996a-4891-a745-aadfcdec225a",
      "phoneNumber": "+447123456789",
      "packCode": "string"
    }
  }
}';
const headers = {
  'Content-Type':'application/json',
  'Accept':'*/*'
};

fetch('http://localhost:64117/sms-fulfilment',
{
  method: 'POST',
  body: inputBody,
  headers: headers
})
.then(function(res) {
    return res.json();
}).then(function(body) {
    console.log(body);
});

```

```ruby
require 'rest-client'
require 'json'

headers = {
  'Content-Type' => 'application/json',
  'Accept' => '*/*'
}

result = RestClient.post 'http://localhost:64117/sms-fulfilment',
  params: {
  }, headers: headers

p JSON.parse(result)

```

```python
import requests
headers = {
  'Content-Type': 'application/json',
  'Accept': '*/*'
}

r = requests.post('http://localhost:64117/sms-fulfilment', headers = headers)

print(r.json())

```

```php
<?php

require 'vendor/autoload.php';

$headers = array(
    'Content-Type' => 'application/json',
    'Accept' => '*/*',
);

$client = new \GuzzleHttp\Client();

// Define array of request body.
$request_body = array();

try {
    $response = $client->request('POST','http://localhost:64117/sms-fulfilment', array(
        'headers' => $headers,
        'json' => $request_body,
       )
    );
    print_r($response->getBody()->getContents());
 }
 catch (\GuzzleHttp\Exception\BadResponseException $e) {
    // handle exception or api errors.
    print_r($e->getMessage());
 }

 // ...

```

```java
URL obj = new URL("http://localhost:64117/sms-fulfilment");
HttpURLConnection con = (HttpURLConnection) obj.openConnection();
con.setRequestMethod("POST");
int responseCode = con.getResponseCode();
BufferedReader in = new BufferedReader(
    new InputStreamReader(con.getInputStream()));
String inputLine;
StringBuffer response = new StringBuffer();
while ((inputLine = in.readLine()) != null) {
    response.append(inputLine);
}
in.close();
System.out.println(response.toString());

```

```go
package main

import (
       "bytes"
       "net/http"
)

func main() {

    headers := map[string][]string{
        "Content-Type": []string{"application/json"},
        "Accept": []string{"*/*"},
    }

    data := bytes.NewBuffer([]byte{jsonReq})
    req, err := http.NewRequest("POST", "http://localhost:64117/sms-fulfilment", data)
    req.Header = headers

    client := &http.Client{}
    resp, err := client.Do(req)
    // ...
}

```

`POST /sms-fulfilment`

> Body parameter

```json
{
  "header": {
    "source": "Survey Enquiry Line API",
    "channel": "RH",
    "correlationId": "48fb4cd3-2ef6-4479-bea1-7c92721b988c",
    "originatingUser": "fred.bloggs@ons.gov.uk"
  },
  "payload": {
    "smsFulfilment": {
      "caseId": "af51d69f-996a-4891-a745-aadfcdec225a",
      "phoneNumber": "+447123456789",
      "packCode": "string"
    }
  }
}
```

<h3 id="smsfulfilment-parameters">Parameters</h3>

|Name|In|Type|Required|Description|
|---|---|---|---|---|
|body|body|[RequestDTO](#schemarequestdto)|true|none|

> Example responses

> 200 Response

<h3 id="smsfulfilment-responses">Responses</h3>

|Status|Meaning|Description|Schema|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|OK|[SmsFulfilmentResponse](#schemasmsfulfilmentresponse)|

<aside class="success">
This operation does not require authentication
</aside>

# Schemas

<h2 id="tocS_RequestDTO">RequestDTO</h2>
<!-- backwards compatibility -->
<a id="schemarequestdto"></a>
<a id="schema_RequestDTO"></a>
<a id="tocSrequestdto"></a>
<a id="tocsrequestdto"></a>

```json
{
  "header": {
    "source": "Survey Enquiry Line API",
    "channel": "RH",
    "correlationId": "48fb4cd3-2ef6-4479-bea1-7c92721b988c",
    "originatingUser": "fred.bloggs@ons.gov.uk"
  },
  "payload": {
    "smsFulfilment": {
      "caseId": "af51d69f-996a-4891-a745-aadfcdec225a",
      "phoneNumber": "+447123456789",
      "packCode": "string"
    }
  }
}

```

### Properties

|Name|Type|Required|Restrictions|Description|
|---|---|---|---|---|
|header|[RequestHeaderDTO](#schemarequestheaderdto)|false|none|none|
|payload|[RequestPayloadDTO](#schemarequestpayloaddto)|false|none|none|

<h2 id="tocS_RequestHeaderDTO">RequestHeaderDTO</h2>
<!-- backwards compatibility -->
<a id="schemarequestheaderdto"></a>
<a id="schema_RequestHeaderDTO"></a>
<a id="tocSrequestheaderdto"></a>
<a id="tocsrequestheaderdto"></a>

```json
{
  "source": "Survey Enquiry Line API",
  "channel": "RH",
  "correlationId": "48fb4cd3-2ef6-4479-bea1-7c92721b988c",
  "originatingUser": "fred.bloggs@ons.gov.uk"
}

```

### Properties

|Name|Type|Required|Restrictions|Description|
|---|---|---|---|---|
|source|string|false|none|The microservice that is calling the API|
|channel|string|false|none|The product that is calling the API|
|correlationId|string(uuid)|false|none|The ID that connects all the way from the public web load balancer to the backend, and back again|
|originatingUser|string|false|none|The ONS user who is triggering this API request via an internal UI|

<h2 id="tocS_RequestPayloadDTO">RequestPayloadDTO</h2>
<!-- backwards compatibility -->
<a id="schemarequestpayloaddto"></a>
<a id="schema_RequestPayloadDTO"></a>
<a id="tocSrequestpayloaddto"></a>
<a id="tocsrequestpayloaddto"></a>

```json
{
  "smsFulfilment": {
    "caseId": "af51d69f-996a-4891-a745-aadfcdec225a",
    "phoneNumber": "+447123456789",
    "packCode": "string"
  }
}

```

### Properties

|Name|Type|Required|Restrictions|Description|
|---|---|---|---|---|
|smsFulfilment|[SmsFulfilment](#schemasmsfulfilment)|false|none|none|

<h2 id="tocS_SmsFulfilment">SmsFulfilment</h2>
<!-- backwards compatibility -->
<a id="schemasmsfulfilment"></a>
<a id="schema_SmsFulfilment"></a>
<a id="tocSsmsfulfilment"></a>
<a id="tocssmsfulfilment"></a>

```json
{
  "caseId": "af51d69f-996a-4891-a745-aadfcdec225a",
  "phoneNumber": "+447123456789",
  "packCode": "string"
}

```

### Properties

|Name|Type|Required|Restrictions|Description|
|---|---|---|---|---|
|caseId|string(uuid)|false|none|The case, which must exist in RM|
|phoneNumber|string|false|none|The phone number, which must be a UK number consisting of 9 digits, preceded by a `7` and optionally a UK country code or zero (`0`, `044` or `+44`).|
|packCode|string|false|none|The pack code, which must exist in RM and the pack code must be allowed on the survey the case belongs to|

<h2 id="tocS_SmsFulfilmentResponse">SmsFulfilmentResponse</h2>
<!-- backwards compatibility -->
<a id="schemasmsfulfilmentresponse"></a>
<a id="schema_SmsFulfilmentResponse"></a>
<a id="tocSsmsfulfilmentresponse"></a>
<a id="tocssmsfulfilmentresponse"></a>

```json
{}

```

### Properties

*None*

