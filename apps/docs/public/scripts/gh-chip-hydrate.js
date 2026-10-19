/**
 * Client-side refresh for the Almasix theme `.gh-chip` (SSR placeholder from build).
 * Fetches api.github.com with sessionStorage cache (10 minutes).
 */
(function () {
  var REPO = 'konaframework/kona';
  var CACHE_KEY = 'kona-gh-chip-v1';
  var TTL_MS = 10 * 60 * 1000;

  function formatCount(n) {
    if (typeof n !== 'number' || !isFinite(n)) return '—';
    if (n >= 1000) {
      var k = n / 1000;
      return (k >= 10 ? Math.round(k) : k.toFixed(1).replace(/\.0$/, '')) + 'k';
    }
    return String(n);
  }

  function readCache() {
    try {
      var raw = sessionStorage.getItem(CACHE_KEY);
      if (!raw) return null;
      var parsed = JSON.parse(raw);
      if (!parsed || typeof parsed.ts !== 'number' || !parsed.data) return null;
      if (Date.now() - parsed.ts > TTL_MS) return null;
      return parsed.data;
    } catch (_) {
      return null;
    }
  }

  function writeCache(data) {
    try {
      sessionStorage.setItem(CACHE_KEY, JSON.stringify({ ts: Date.now(), data: data }));
    } catch (_) {
      /* ignore quota / private mode */
    }
  }

  function apply(chip, data) {
    if (!chip || !data) return;
    var parts = chip.querySelectorAll('.gh-chip__part');
    for (var i = 0; i < parts.length; i++) {
      var part = parts[i];
      var title = part.getAttribute('title') || '';
      var span = part.querySelector('span');
      if (title.indexOf('stars') !== -1 && span && data.stars != null) {
        span.textContent = formatCount(data.stars);
      }
      if (title.indexOf('forks') !== -1 && span && data.forks != null) {
        span.textContent = formatCount(data.forks);
      }
      if (part.classList.contains('gh-chip__release') && data.release) {
        part.textContent = data.release;
        part.hidden = false;
      }
    }
    if (data.release) {
      var release = chip.querySelector('.gh-chip__release');
      if (!release) {
        var first = chip.firstElementChild;
        var a = document.createElement('a');
        a.className = 'gh-chip__part gh-chip__release';
        a.href = 'https://github.com/' + REPO + '/releases';
        a.textContent = data.release;
        chip.insertBefore(a, first);
      }
    }
  }

  function fetchJson(url) {
    return fetch(url, {
      headers: {
        Accept: 'application/vnd.github+json',
        'X-GitHub-Api-Version': '2022-11-28',
      },
    }).then(function (res) {
      return res.ok ? res.json() : null;
    }).catch(function () {
      return null;
    });
  }

  function refresh() {
    var chip = document.querySelector('.gh-chip');
    if (!chip) return;

    var cached = readCache();
    if (cached) {
      apply(chip, cached);
      return;
    }

    Promise.all([
      fetchJson('https://api.github.com/repos/' + REPO),
      fetchJson('https://api.github.com/repos/' + REPO + '/releases/latest'),
    ]).then(function (results) {
      var repo = results[0];
      var release = results[1];
      var data = {
        stars: repo && typeof repo.stargazers_count === 'number' ? repo.stargazers_count : null,
        forks: repo && typeof repo.forks_count === 'number' ? repo.forks_count : null,
        release: release && typeof release.tag_name === 'string' ? release.tag_name : null,
      };
      if (data.stars == null && data.forks == null && !data.release) return;
      writeCache(data);
      apply(chip, data);
    });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', refresh);
  } else {
    refresh();
  }
})();
