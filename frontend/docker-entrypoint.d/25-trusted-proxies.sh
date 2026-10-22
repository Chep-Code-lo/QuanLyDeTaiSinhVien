#!/bin/sh
set -eu

header="${NGINX_REAL_IP_HEADER:-CF-Connecting-IP}"
case "$header" in CF-Connecting-IP|X-Forwarded-For) ;; *) echo 'Unsupported trusted proxy IP header' >&2; exit 1 ;; esac
peers=$(printf '%s' "${NGINX_TRUSTED_PROXY_CIDRS:-}" | tr ',' ' ')
# Do not interpolate arbitrary configuration text or allow a catch-all trust range.
for peer in $peers; do
    case "$peer" in *[!0-9a-fA-F.:/]*|0.0.0.0/0|::/0) echo 'Invalid trusted proxy address' >&2; exit 1 ;; esac
done
config=/etc/nginx/conf.d/00-trusted-proxies.conf
printf 'geo $realip_remote_addr $trusted_upstream {\n    default 0;\n' > "$config"
for peer in $peers; do printf '    %s 1;\n' "$peer" >> "$config"; done
printf '}\n' >> "$config"
for peer in $peers; do printf 'set_real_ip_from %s;\n' "$peer" >> "$config"; done
printf 'real_ip_header %s;\nreal_ip_recursive off;\n' "$header" >> "$config"
