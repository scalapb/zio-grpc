# grpc-web-service frontend helloworld Makefile

img="envoyproxy/envoy-alpine:v1.15-latest"

proto:
	@protoc -I=../src/main/protobuf helloworld.proto \
  	--js_out=import_style=commonjs:. \
		--grpc-web_out=import_style=commonjs,mode=grpcwebtext:.

proxy:
	@docker run -d -v "$(pwd)"/envoy.yaml:/etc/envoy/envoy.yaml:ro --network=host $(img)

build:
	@ncu -u 
	@npm install 
	@npx webpack client.js

run:
	@python3 -m http.server 8081

clean:
	@rm -rf node_modules dist
