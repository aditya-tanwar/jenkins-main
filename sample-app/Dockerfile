FROM docker.io/nginx:alpine
RUN apk update && apk upgrade --no-cache
RUN printf '<h1>Hello from Jenkins + Syft + Grype + Cosign!</h1>\n' > /usr/share/nginx/html/index.html
EXPOSE 80