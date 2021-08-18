#!/bin/sh

# Wait for pubsub-emulator to come up
bash -c 'while [[ "$(curl -s -o /dev/null -w ''%{http_code}'' '$PUBSUB_SETUP_HOST')" != "200" ]]; do sleep 1; done'

# Dummy topic for testing

curl -X PUT http://$PUBSUB_SETUP_HOST/v1/projects/our-project/topics/rm-internal-sms-fulfilment-dummy
curl -X PUT http://$PUBSUB_SETUP_HOST/v1/projects/our-project/subscriptions/rm-internal-sms-fulfilment_notify-service-it -H 'Content-Type: application/json' -d '{"topic": "projects/project/topics/rm-internal-sms-fulfilment-dummy"}'

# Below are the minimum RM topics & events - ideally external systems will publish/subscribe to these, in the correct standardised format

curl -X PUT http://$PUBSUB_SETUP_HOST/v1/projects/our-project/topics/rm-internal-telephone-capture
curl -X PUT http://$PUBSUB_SETUP_HOST/v1/projects/our-project/subscriptions/rm-internal-telephone-capture_case-processor -H 'Content-Type: application/json' -d '{"topic": "projects/our-project/topics/rm-internal-telephone-capture"}'

curl -X PUT http://$PUBSUB_SETUP_HOST/v1/projects/our-project/topics/rm-internal-sample
curl -X PUT http://$PUBSUB_SETUP_HOST/v1/projects/our-project/subscriptions/rm-internal-sample_case-processor -H 'Content-Type: application/json' -d '{"topic": "projects/our-project/topics/rm-internal-sample"}'

curl -X PUT http://$PUBSUB_SETUP_HOST/v1/projects/our-project/topics/rm-internal-print-row
curl -X PUT http://$PUBSUB_SETUP_HOST/v1/projects/our-project/subscriptions/rm-internal-print-row_print-file-service -H 'Content-Type: application/json' -d '{"topic": "projects/our-project/topics/rm-internal-print-row"}'

curl -X PUT http://$PUBSUB_SETUP_HOST/v1/projects/our-project/topics/rm-internal-sms-fulfilment
curl -X PUT http://$PUBSUB_SETUP_HOST/v1/projects/our-project/subscriptions/rm-internal-sms-fulfilment_case-processor -H 'Content-Type: application/json' -d '{"topic": "projects/our-project/topics/rm-internal-sms-fulfilment"}'

curl -X PUT http://$PUBSUB_SETUP_HOST/v1/projects/shared-project/topics/event_receipt
curl -X PUT http://$PUBSUB_SETUP_HOST/v1/projects/shared-project/subscriptions/event_receipt_rm-case-processor -H 'Content-Type: application/json' -d '{"topic": "projects/shared-project/topics/event_receipt"}'

curl -X PUT http://$PUBSUB_SETUP_HOST/v1/projects/shared-project/topics/event_refusal
curl -X PUT http://$PUBSUB_SETUP_HOST/v1/projects/shared-project/subscriptions/event_refusal_rm-case-processor -H 'Content-Type: application/json' -d '{"topic": "projects/shared-project/topics/event_refusal"}'

curl -X PUT http://$PUBSUB_SETUP_HOST/v1/projects/shared-project/topics/event_invalid-case
curl -X PUT http://$PUBSUB_SETUP_HOST/v1/projects/shared-project/subscriptions/event_invalid-case_rm-case-processor -H 'Content-Type: application/json' -d '{"topic": "projects/shared-project/topics/event_invalid-case"}'

curl -X PUT http://$PUBSUB_SETUP_HOST/v1/projects/shared-project/topics/event_survey-launch
curl -X PUT http://$PUBSUB_SETUP_HOST/v1/projects/shared-project/subscriptions/event_survey-launch_rm-case-processor -H 'Content-Type: application/json' -d '{"topic": "projects/shared-project/topics/event_survey-launch"}'

curl -X PUT http://$PUBSUB_SETUP_HOST/v1/projects/shared-project/topics/event_uac-authentication
curl -X PUT http://$PUBSUB_SETUP_HOST/v1/projects/shared-project/subscriptions/event_uac-authentication_rm-case-processor -H 'Content-Type: application/json' -d '{"topic": "projects/shared-project/topics/event_uac-authentication"}'

curl -X PUT http://$PUBSUB_SETUP_HOST/v1/projects/shared-project/topics/event_print-fulfilment
curl -X PUT http://$PUBSUB_SETUP_HOST/v1/projects/shared-project/subscriptions/event_print-fulfilment_rm-case-processor -H 'Content-Type: application/json' -d '{"topic": "projects/shared-project/topics/event_print-fulfilment"}'

curl -X PUT http://$PUBSUB_SETUP_HOST/v1/projects/shared-project/topics/event_deactivate-uac
curl -X PUT http://$PUBSUB_SETUP_HOST/v1/projects/shared-project/subscriptions/event_deactivate-uac_rm-case-processor -H 'Content-Type: application/json' -d '{"topic": "projects/shared-project/topics/event_deactivate-uac"}'

curl -X PUT http://$PUBSUB_SETUP_HOST/v1/projects/shared-project/topics/event_update-sample-sensitive
curl -X PUT http://$PUBSUB_SETUP_HOST/v1/projects/shared-project/subscriptions/event_update-sample-sensitive_rm-case-processor -H 'Content-Type: application/json' -d '{"topic": "projects/shared-project/topics/event_update-sample-sensitive"}'

curl -X PUT http://$PUBSUB_SETUP_HOST/v1/projects/shared-project/topics/event_case-update
curl -X PUT http://$PUBSUB_SETUP_HOST/v1/projects/shared-project/subscriptions/event_case-update_rh -H 'Content-Type: application/json' -d '{"topic": "projects/shared-project/topics/event_case-update"}'

curl -X PUT http://$PUBSUB_SETUP_HOST/v1/projects/shared-project/topics/event_uac-update
curl -X PUT http://$PUBSUB_SETUP_HOST/v1/projects/shared-project/subscriptions/event_uac-update_rh -H 'Content-Type: application/json' -d '{"topic": "projects/shared-project/topics/event_uac-update"}'
