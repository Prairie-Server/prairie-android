# .lrc/ — Repository Rules

Teaches [git-lrc](https://github.com/HexmosTech/git-lrc) / LiveReview about this Prairie repository.

- `rules/*.md` — short reviewer guidance (bundle capped at 3000 characters)
- `ignore` — paths excluded from AI review

Validate locally after installing git-lrc:

```bash
lrc config check
lrc config preview
```

Machine-wide install (once per developer):

```bash
curl -fsSL https://hexmos.com/lrc-install.sh | bash
git lrc setup
```
