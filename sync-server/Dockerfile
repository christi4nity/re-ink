FROM golang:1.22-alpine AS builder

WORKDIR /app
COPY go.mod go.sum ./
RUN go mod download

COPY *.go ./
RUN CGO_ENABLED=0 go build -o reink-sync .

FROM alpine:3.19

RUN apk add --no-cache ca-certificates

COPY --from=builder /app/reink-sync /usr/local/bin/reink-sync

EXPOSE 8073
VOLUME /data

ENV DATA_DIR=/data
ENV PORT=8073

ENTRYPOINT ["reink-sync"]
