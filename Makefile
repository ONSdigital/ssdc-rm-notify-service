build:
	mvn clean install

build-no-test:
	mvn clean install -Dmaven.test.skip=true -DdockerCompose.skip=true

format:
	mvn fmt:format

format-check:
	mvn fmt:check

check:
	mvn compile fmt:check pmd:check

test:
	mvn clean verify jacoco:report

docker-build:
	docker build . -t europe-west2-docker.pkg.dev/ssdc-rm-ci/docker/ssdc-rm-notify-service:latest