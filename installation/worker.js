const SOURCE = "https://raw.githubusercontent.com/jenesis/jenesis/main/installation/install.sh";

export default {
  async fetch(request) {
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
      },
    });
  },
};
