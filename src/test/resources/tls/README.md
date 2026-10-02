The PEM certificate and private key in this directory are public test fixtures only.
They are used by local TLS echo servers to verify certificate rejection and explicit opt-out.

To regenerate both files from this directory:

```sh
openssl req -x509 -newkey rsa:2048 -noenc -keyout self-signed-server-key.pem -out self-signed-server-cert.pem -days 36500 -subj '/CN=ircafe-test.invalid' -addext 'subjectAltName=DNS:ircafe-test.invalid'
```
