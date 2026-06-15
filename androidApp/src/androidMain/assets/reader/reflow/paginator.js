(function () {
  var root = document.getElementById('reflow-root');
  var styleEl = document.getElementById('reflow-style');
  var page = 0, pageCount = 1;
  // Viewport in CSS px, pushed from the Android side (Compose-measured size).
  // Android WebView resolves `vh`/`vw` to 0 when the page loads before the view
  // is laid out, so we never rely on viewport units for the page box.
  var vpW = 0, vpH = 0;
  function send(o){ try{ AndroidReflow.onEvent(JSON.stringify(o)); }catch(e){} }
  function viewW(){ return vpW || window.innerWidth || document.documentElement.clientWidth; }
  function applyViewport(){
    if (vpW > 0) root.style.columnWidth = vpW + 'px';
    if (vpH > 0) root.style.height = vpH + 'px';
  }
  function measure(){
    pageCount = Math.max(1, Math.round(root.scrollWidth / viewW()));
    send({type:'paginated', pageCount: pageCount});
  }
  function apply(){ root.style.transform = 'translateX(' + (-page * viewW()) + 'px)'; }
  function relocate(){
    send({type:'relocated', page: page, pageProgression: pageCount>1 ? page/(pageCount-1) : 0});
  }
  function remeasureKeepingProgress(){
    var frac = pageCount>1?page/(pageCount-1):0;
    measure();
    page = Math.round(frac*(pageCount-1));
    apply(); relocate();
  }
  window.ReflowApi = {
    setViewport: function(w, h){
      vpW = w; vpH = h; applyViewport();
      requestAnimationFrame(remeasureKeepingProgress);
    },
    load: function(html, baseUrl){
      var b = document.querySelector('base'); if(!b){ b=document.createElement('base'); document.head.appendChild(b);}
      if(baseUrl) b.href = baseUrl;
      root.innerHTML = html; page = 0; applyViewport(); apply();
      requestAnimationFrame(function(){ requestAnimationFrame(function(){ measure(); apply(); relocate(); }); });
    },
    goToPage: function(n){ page = Math.min(Math.max(0, n), pageCount-1); apply(); relocate(); },
    applyStyle: function(css){ styleEl.textContent = css; applyViewport();
      requestAnimationFrame(remeasureKeepingProgress); }
  };
  window.addEventListener('resize', remeasureKeepingProgress);
  send({type:'ready'});
})();
