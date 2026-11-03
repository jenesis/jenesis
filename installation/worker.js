const SOURCE = "https://raw.githubusercontent.com/jenesis/jenesis/main/installation/install.sh";
const REFUSED = "echo 'get.jenesis.build answers over HTTPS only: curl -fsSL https://get.jenesis.build | bash' >&2\nexit 1\n";

export default {
  async fetch(request) {
    if (new URL(request.url).protocol !== "https:") {
      return new Response(REFUSED, { status: 403, headers: { "content-type": "text/plain; charset=utf-8" } });
    }
    if (request.method !== "GET" && request.method !== "HEAD") {
      return new Response("Method Not Allowed", { status: 405, headers: { Allow: "GET, HEAD" } });
    }
    const upstream = await fetch(SOURCE, { cf: { cacheTtl: 300, cacheEverything: true } });
    if (!upstream.ok) {
      return new Response("Upstream fetch failed: " + upstream.status, { status: 502 });
    }
    return new Response(upstream.body, {
      status: 200,
      headers: {
        "content-type": "text/x-shellscript; charset=utf-8",
        "cache-control": "public, max-age=300",
        "x-source": SOURCE,
        "strict-transport-security": "max-age=31536000",
      },
    });
  },
};
