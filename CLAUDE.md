# CLAUDE.md

Bu depo hem `go-ibax` projesini hem de sahibinin kişisel ajan ofisini barındırır.

- Ofis kuralları ve roller: `office/OFIS.md`
- Rol talimatları: `.claude/agents/*.md`
- Oturumun başlığı veya ilk mesajı bir rol belirtiyorsa (Müdür, Kodcu, Araştırmacı, İnceleyici, Viral), o rolün dosyasını oku ve ona göre davran.

## Proje
- Go 1.17+ blokzincir platformu. Giriş: `main.go`, komutlar `cmd/`, çekirdek `packages/`.
- Derleme: `make build` · Test: `go test ./...`
