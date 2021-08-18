# SSDC RM Notify Service
A service for making requests to Gov Notify

## Building
To run all the tests and build the image
```  
   mvn clean install
```

Just build the image
```
    mvn -DskipTests -DskipITs -DdockerCompose.skip
```

## Running Locally

You can run this service dockerised with [docker dev](https://github.com/ONSdigital/ssdc-rm-docker-dev). 

If you wish to run it from an IDE to debug first make sure you've set these environment variables in the run configuration so that it uses your local backing services, then spin up docker dev as usual and stop the `notifyservice` container so it does not conflict. 

```shell
SPRING_CLOUD_GCP_PUBSUB_EMULATOR_HOST=localhost:8538
SPRING_CLOUD_GCP_PUBSUB_PROJECT_ID=project
NOTIFY_BASEURL=http://localhost:8917
```

## Endpoints
### SMS Fulfilment
Endpoint: `/sms-fulfilment`

Method: `post`

Description: Request an SMS fulfilment for a case. The case ID and pack code in the payload must exist in RM and the pack code must be allowed on the survey the case belongs to, otherwise the response will be `400`. 

The phone number must be a UK number consisting of 9 digits, preceded by a `7` and optionally a UK country code or zero (`0`, `044` or `+44`). 

Example body:
```json
{
  "header": {
    "source": "TEST",
    "channel": "TEST",
    "correlationId": "d6fc3b21-368e-43b5-baad-ef68d1a08629",
    "originatingUser": "dummy@example.com"
  },
  "payload": {
    "smsFulfilment": {
      "caseId": "2f2dc309-37cf-4749-85ea-ccb76ee69e4d",
      "packCode": "TEST_SMS",
      "phoneNumber": "(+44) 7123456789"
    }
  }
}
```
