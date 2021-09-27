#!/bin/sh

# Wait for pubsub-emulator to come up
bash -c 'while [[ "$(curl -s -o /dev/null -w ''%{http_code}'' '$PUBSUB_SETUP_HOST')" != "200" ]]; do sleep 1; done'

# Dummy topic for testing

curl -X PUT http://$PUBSUB_SETUP_HOST/v1/projects/our-project/topics/rm-internal-sms-fulfilment-dummy
curl -X PUT http://$PUBSUB_SETUP_HOST/v1/projects/our-project/subscriptions/rm-internal-sms-fulfilment_notify-service-it  -H 'Content-Type: application/json' -d '{"topic": "projects/our-project/topics/rm-internal-sms-fulfilment-dummy"}'

curl -X PUT http://$PUBSUB_SETUP_HOST/v1/projects/our-project/topics/rm-internal-sms-request
curl -X PUT http://$PUBSUB_SETUP_HOST/v1/projects/our-project/subscriptions/rm-internal-sms-request_notify-service -H 'Content-Type: application/json' -d '{"topic": "projects/our-project/topics/rm-internal-sms-request"}'

curl -X PUT http://$PUBSUB_SETUP_HOST/v1/projects/our-project/topics/rm-internal-sms-request-enriched
curl -X PUT http://$PUBSUB_SETUP_HOST/v1/projects/our-project/subscriptions/rm-internal-sms-request-enriched_notify-service -H 'Content-Type: application/json' -d '{"topic": "projects/our-project/topics/rm-internal-sms-request-enriched"}'
curl -X PUT http://$PUBSUB_SETUP_HOST/v1/projects/our-project/subscriptions/TEST-sms-request-enriched_notify-service -H 'Content-Type: application/json' -d '{"topic": "projects/our-project/topics/rm-internal-sms-request-enriched"}'
