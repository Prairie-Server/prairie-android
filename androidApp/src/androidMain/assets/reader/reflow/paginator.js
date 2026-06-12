(function () {
  var root = document.getElementById('reflow-root');
  var styleEl = document.getElementById('reflow-style');
  var page = 0, pageCount = 1;
  function send(o){ try{ AndroidReflow.onEvent(JSON.stringify(o)); }catch(e){} }
  function viewW(){ return window.innerWidth || document.documentElement.clientWidth; }
  function measure(){
    pageCount = Math.max(1, Math.round(root.scrollWidth / viewW()));
    send({type:'paginated', pageCount: pageCount});
  }
  function apply(){ root.style.transform = 'translateX(' + (-page * viewW()) + 'px)'; }
  function relocate(){
    send({type:'relocated', page: page, pageProgression: pageCount>1 ? page/(pageCount-1) : 0});
  }
  window.ReflowApi = {
    load: function(html, baseUrl){
      var b = document.querySelector('base'); if(!b){ b=document.createElement('base'); document.head.appendChild(b);}
      if(baseUrl) b.href = baseUrl;
      root.innerHTML = html; page = 0; apply();
      requestAnimationFrame(function(){ requestAnimationFrame(function(){ measure(); apply(); relocate(); }); });
    },
    goToPage: function(n){ page = Math.min(Math.max(0, n), pageCount-1); apply(); relocate(); },
    applyStyle: function(css){ styleEl.textContent = '#reflow-root{'+css+'}';
      requestAnimationFrame(function(){ var frac = pageCount>1?page/(pageCount-1):0; measure();
        page = Math.round(frac*(pageCount-1)); apply(); relocate(); }); }
  };
  window.addEventListener('resize', function(){ var frac = pageCount>1?page/(pageCount-1):0;
    measure(); page = Math.round(frac*(pageCount-1)); apply(); relocate(); });
  send({type:'ready'});
})();
