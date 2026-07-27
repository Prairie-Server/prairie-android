(function () {
  var root = document.getElementById('reflow-root');
  var styleEl = document.getElementById('reflow-style');
  var page = 0, pageCount = 1;
  var privateOrigin = 'https://appassets.androidplatform.net';
  // Viewport in CSS px, pushed from the Android side (Compose-measured size).
  // Android WebView resolves `vh`/`vw` to 0 when the page loads before the view
  // is laid out, so we never rely on viewport units for the page box.
  var vpW = 0, vpH = 0;
  function send(o){ try{ AndroidReflow.onEvent(JSON.stringify(o)); }catch(e){} }
  function viewW(){ return vpW || window.innerWidth || document.documentElement.clientWidth; }
  function rootPaddingX(){
    var style = window.getComputedStyle(root);
    var paddingLeft = parseFloat(style.paddingLeft) || 0;
    var paddingRight = parseFloat(style.paddingRight) || 0;
    var horizontalPadding = paddingLeft + paddingRight;
    return horizontalPadding;
  }
  function applyViewport(){
    var horizontalPadding = rootPaddingX();
    if (vpW > 0) root.style.columnWidth = Math.max(1, viewW() - horizontalPadding) + 'px';
    root.style.columnGap = horizontalPadding + 'px';
    if (vpH > 0) root.style.height = vpH + 'px';
  }
  function measure(){
    var width = Math.max(1, viewW());
    pageCount = Math.max(1, Math.ceil(root.scrollWidth / width));
    send({type:'paginated', pageCount: pageCount});
  }
  function apply(){ root.scrollLeft = page * viewW(); }
  function relocate(){
    send({type:'relocated', page: page, pageProgression: pageCount>1 ? page/(pageCount-1) : 0});
  }
  function remeasureKeepingProgress(){
    var frac = pageCount>1?page/(pageCount-1):0;
    measure();
    page = Math.round(frac*(pageCount-1));
    apply(); relocate();
  }
  function remeasureAfterPendingImages(){
    Array.prototype.forEach.call(root.querySelectorAll('img'), function(img){
      if (img.complete) return;
      function onLoad(){
        img.removeEventListener('load', onLoad);
        remeasureKeepingProgress();
      }
      img.addEventListener('load', onLoad);
    });
  }
  function rewriteResourceUrls(html, baseUrl){
    var template = document.createElement('template');
    template.innerHTML = html;
    if (!baseUrl) return template.innerHTML;

    var base;
    try {
      base = new URL(baseUrl, privateOrigin);
      var bookPathMatch = base.pathname.match(/^\/epub\/(epub-[0-9a-f]{40})(?:\/|$)/);
      if (base.origin !== privateOrigin || !bookPathMatch) {
        return template.innerHTML;
      }
      var allowedBookPath = '/epub/' + bookPathMatch[1] + '/';
    } catch (e) {
      return template.innerHTML;
    }

    var attributes = ['href', 'src', 'xlink:href'];
    Array.prototype.forEach.call(template.content.querySelectorAll('*'), function(element){
      attributes.forEach(function(attribute){
        if (!element.hasAttribute(attribute)) return;
        var value = element.getAttribute(attribute);
        if (!value || value.charAt(0) === '#') return;
        try {
          var resolved = new URL(value, base);
          if (resolved.origin === privateOrigin && resolved.pathname.indexOf(allowedBookPath) === 0) {
            element.setAttribute(attribute, resolved.href);
          } else {
            element.removeAttribute(attribute);
          }
        } catch (e) {
          element.removeAttribute(attribute);
        }
      });
    });
    return template.innerHTML;
  }
  window.ReflowApi = {
    setViewport: function(w, h){
      vpW = w; vpH = h; applyViewport();
      requestAnimationFrame(remeasureKeepingProgress);
    },
    load: function(html, baseUrl){
      root.innerHTML = rewriteResourceUrls(html, baseUrl);
      remeasureAfterPendingImages(); page = 0; applyViewport(); apply();
      requestAnimationFrame(function(){ requestAnimationFrame(function(){ measure(); apply(); relocate(); }); });
    },
    goToPage: function(n){ page = Math.min(Math.max(0, n), pageCount-1); apply(); relocate(); },
    applyStyle: function(css){ styleEl.textContent = css; applyViewport();
      requestAnimationFrame(remeasureKeepingProgress); }
  };
  window.addEventListener('resize', remeasureKeepingProgress);
  send({type:'ready'});
})();
