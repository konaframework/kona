package org.konaframework.firefly.support

import org.konaframework.crema.CremaEngine

enum class ChromeMode {
    PANEL,
    AUTH,
}

class ChromeContext @JvmOverloads constructor(
    val theme: FireflyTheme,
    val hooks: RenderHooks,
    val partials: Map<String, String>,
    val css: String,
    val csrfToken: String? = null,
    val documentTitle: String = "Firefly",
    val brandName: String = "Firefly",
    val mode: ChromeMode = ChromeMode.PANEL,
    /** Panel default content max-width token (`xs`…`7xl`, `full`, `screen`, `prose`). */
    val maxWidth: String? = null,
)

interface PanelLayout {
    fun render(context: ChromeContext): String
}

/**
 * Default authenticated panel document.
 *
 * Phase A (Wire + Crema rule): markup lives in
 * `views/firefly/layouts/panel.crema.html`. This class is the thin
 * [PanelLayout] adapter — it validates partials, packs data, and asks Crema to
 * render. A Wire page-host component is deferred; auth stays on [AuthLayout].
 */
class DefaultLayout(
    private val crema: CremaEngine,
) : PanelLayout {
    /** Default engine: package Crema layouts via [FireflyCrema]. */
    constructor() : this(FireflyCrema.engine())

    override fun render(context: ChromeContext): String {
        if (context.mode == ChromeMode.AUTH) {
            return AuthLayout().render(context)
        }
        val sidebar = part(context, "sidebar")
        val topbar = part(context, "topbar")
        val body = part(context, "body")
        val footer = part(context, "footer")
        val tokens = context.theme.tokens()
        val csrf = context.csrfToken?.takeUnless { it.isBlank() }
        val title = context.documentTitle.ifBlank { "Firefly" }
        val maxWidth = context.maxWidth?.takeUnless { it.isBlank() }
        return crema.render(
            FireflyCrema.PANEL,
            mapOf(
                "documentTitle" to title,
                "brandName" to context.brandName,
                "csrfToken" to csrf,
                "css" to context.css,
                "sidebar" to sidebar,
                "topbar" to topbar,
                "body" to body,
                "footer" to footer,
                "themeInitScript" to THEME_INIT_SCRIPT,
                "chromeScript" to CHROME_SCRIPT,
                "bodyBackground" to tokens.bodyBackground,
                "bodyColor" to tokens.heading,
                "maxWidth" to maxWidth,
            ),
        )
    }

    private fun part(context: ChromeContext, name: String): String {
        val html = context.partials[name]
        if (html == null) throw FireflyException("Missing partial [$name]")
        return html
    }

    companion object {
        /**
         * Applies the stored theme preference before first paint so dark panels
         * never flash light. Toggles `ff-dark` on the document root; dark tokens
         * respond to both `.ff-dark .ff-panel` and `.ff-panel.ff-dark`.
         */
        internal const val THEME_INIT_SCRIPT: String =
            "<script>(function(){try{var p=localStorage.getItem('ff-theme')||'system';" +
                "var d=p==='dark'||(p!=='light'&&window.matchMedia('(prefers-color-scheme: dark)').matches);" +
                "document.documentElement.classList.toggle('ff-dark',d);}catch(e){}})();</script>"

        /**
         * Panel chrome behavior with no framework: 3-state theme cycle, dropdown
         * menus (notifications + user), mobile sidebar drawer, Action/TableButton
         * dialogs (`data-ff-dialog-*`), and table soft-nav (`data-ff-table-nav`) that
         * swaps `.ff-table-wrap` via fetch without a full document reload.
         * Alpine is optional — dialogs and table nav open without it.
         */
        internal const val CHROME_SCRIPT: String =
            "<script>(function(){" +
                "var root=document.querySelector('.ff-panel');if(!root)return;" +
                "var media=window.matchMedia('(prefers-color-scheme: dark)');" +
                "function pref(){try{var v=localStorage.getItem('ff-theme');" +
                "return v==='light'||v==='dark'||v==='system'?v:'system';}catch(e){return 'system';}}" +
                "function apply(p){var d=p==='dark'||(p!=='light'&&media.matches);" +
                "document.documentElement.classList.toggle('ff-dark',d);" +
                "root.querySelectorAll('[data-ff-theme-toggle]').forEach(function(b){b.setAttribute('data-ff-theme',p);});}" +
                "function closeMenus(){root.querySelectorAll('[data-ff-menu].ff-open').forEach(function(m){m.classList.remove('ff-open');});}" +
                "function resetDialogForms(panel){if(!panel)return;" +
                "panel.querySelectorAll('form').forEach(function(f){try{f.reset();}catch(e){}});" +
                "panel.querySelectorAll('[data-ff-toggle]').forEach(function(btn){" +
                "var wrap=btn.closest('.ff-toggle-wrap')||btn.parentElement;" +
                "var input=wrap&&wrap.querySelector('[data-ff-toggle-value]');" +
                "var on=!!(input&&input.value==='1');" +
                "btn.setAttribute('aria-pressed',on?'true':'false');" +
                "btn.setAttribute('aria-checked',on?'true':'false');});}" +
                // Drag sets position:fixed + margin:0 + left/top. Clear all of them on
                // close/open so the next open restores stylesheet centering (#316).
                "function resetDialogDrag(panel){if(!panel)return;" +
                "panel.querySelectorAll('.ff-dialog-window').forEach(function(dlg){" +
                "dlg.style.position='';dlg.style.margin='';dlg.style.left='';dlg.style.top='';dlg.style.transform='';});}" +
                "function closeDialog(panel){if(!panel)return;resetDialogForms(panel);resetDialogDrag(panel);" +
                "panel.classList.remove('ff-open');" +
                "panel.setAttribute('hidden','');panel.style.display='none';panel.setAttribute('aria-hidden','true');}" +
                "function closeDialogs(){root.querySelectorAll('[data-ff-dialog-panel].ff-open').forEach(closeDialog);}" +
                "function openDialog(panel){if(!panel)return;closeDialogs();" +
                "panel.classList.add('ff-open');" +
                "panel.removeAttribute('hidden');panel.style.display='block';panel.removeAttribute('aria-hidden');" +
                "resetDialogDrag(panel);}" +
                "function closePickers(except){root.querySelectorAll('[data-ff-picker].ff-picker-open').forEach(function(p){" +
                "if(except&&p===except)return;p.classList.remove('ff-picker-open');" +
                "var panel=p.querySelector('[data-ff-picker-panel]');if(panel){panel.setAttribute('hidden','');}});}" +
                "function openPicker(p){if(!p)return;closePickers(p);p.classList.add('ff-picker-open');" +
                "var panel=p.querySelector('[data-ff-picker-panel]');if(panel)panel.removeAttribute('hidden');}" +
                "function pad2(n){return(n<10?'0':'')+n;}" +
                "function parseYmd(s){if(!s)return null;var m=/^(\\d{4})-(\\d{2})-(\\d{2})/.exec(s);if(!m)return null;" +
                "return{y:+m[1],m:+m[2],d:+m[3]};}" +
                "function ymd(o){return o.y+'-'+pad2(o.m)+'-'+pad2(o.d);}" +
                "function addMonths(o,delta){var dt=new Date(o.y,o.m-1+delta,1);return{y:dt.getFullYear(),m:dt.getMonth()+1,d:1};}" +
                "function daysInMonth(y,m){return new Date(y,m,0).getDate();}" +
                "function monthName(y,m,locale){try{return new Date(y,m-1,1).toLocaleString(locale||undefined,{month:'long'})+' '+y;}" +
                "catch(e){return m+'/'+y;}}" +
                "function disabledSet(p){var raw=p.getAttribute('data-ff-disabled')||'';var out={};" +
                "raw.split(',').forEach(function(x){x=x.trim();if(x)out[x]=1;});return out;}" +
                "function outOfBounds(p,day){var min=p.getAttribute('data-ff-min');var max=p.getAttribute('data-ff-max');" +
                "if(min&&day<min.slice(0,10))return true;if(max&&day>max.slice(0,10))return true;return false;}" +
                "function pickerView(p){return p.getAttribute('data-ff-picker-view')||'days';}" +
                "function setPickerView(p,v){p.setAttribute('data-ff-picker-view',v);}" +
                "function monthShort(m,locale){try{return new Date(2000,m-1,1).toLocaleString(locale||undefined,{month:'short'});}catch(e){return String(m);}}" +
                "function rebuildView(p,focus){var grid=p.querySelector('[data-ff-picker-grid]');if(!grid)return;" +
                "var view=pickerView(p);var kind=p.getAttribute('data-ff-picker-kind')||'date';var locale=p.getAttribute('data-ff-locale');" +
                "var weekdays=p.querySelector('[data-ff-picker-weekdays]');if(weekdays)weekdays.style.display=view==='days'?'grid':'none';" +
                "var cap=p.querySelector('[data-ff-picker-caption]');var html='';var state=p.getAttribute('data-ff-state')||'';" +
                "if(view==='days'){var firstDay=+(p.getAttribute('data-ff-first-day')||'1');var dis=disabledSet(p);" +
                "var start=null,end=null,selected=null;" +
                "if(kind==='range'){var bits=state.split(/\\s+to\\s+|\\s+–\\s+|\\s+-\\s+|\\//);if(bits.length===2){start=bits[0].trim().slice(0,10);end=bits[1].trim().slice(0,10);}}" +
                "else{selected=(parseYmd(state)||null);if(selected)selected=ymd(selected);}" +
                "var first=new Date(focus.y,focus.m-1,1);var dow=first.getDay();" +
                "var lead=firstDay===1?((dow+6)%7):dow;var cursor=new Date(focus.y,focus.m-1,1-lead);" +
                "for(var i=0;i<42;i++){var cy=cursor.getFullYear(),cm=cursor.getMonth()+1,cd=cursor.getDate();" +
                "var iso=cy+'-'+pad2(cm)+'-'+pad2(cd);var outside=cm!==focus.m;var disabled=!!dis[iso]||outOfBounds(p,iso);" +
                "var cls='ff-day';if(outside)cls+=' ff-day-outside';if(disabled)cls+=' ff-day-disabled';" +
                "if(kind==='range'){if(start&&end&&iso>=start&&iso<=end)cls+=' ff-day-in-range';if(iso===start||iso===end)cls+=' ff-day-selected';}" +
                "else if(selected&&iso===selected)cls+=' ff-day-selected';" +
                "html+='<button type=\"button\" class=\"'+cls+'\" data-ff-day=\"'+iso+'\"'+(disabled?' disabled':'')+'>'+cd+'</button>';" +
                "cursor.setDate(cursor.getDate()+1);}grid.className='ff-picker-grid';if(cap)cap.textContent=monthName(focus.y,focus.m,locale);}" +
                "else if(view==='months'){grid.className='ff-picker-grid ff-picker-grid-months';" +
                "for(var m=1;m<=12;m++){var sel=(focus.m===m)?' ff-day-selected':'';" +
                "html+='<button type=\"button\" class=\"ff-day'+sel+'\" data-ff-month=\"'+m+'\">'+monthShort(m,locale)+'</button>';}" +
                "if(cap)cap.textContent=String(focus.y);}" +
                "else if(view==='years'){grid.className='ff-picker-grid ff-picker-grid-years';var y0=Math.floor(focus.y/12)*12;" +
                "for(var y=y0;y<y0+12;y++){var sel=(focus.y===y)?' ff-day-selected':'';" +
                "html+='<button type=\"button\" class=\"ff-day'+sel+'\" data-ff-year=\"'+y+'\">'+y+'</button>';}" +
                "if(cap)cap.textContent=y0+' – '+(y0+11);}" +
                "else{grid.className='ff-picker-grid ff-picker-grid-decades';var d0=Math.floor(focus.y/100)*100;" +
                "for(var d=d0;d<d0+120;d+=10){var sel=(focus.y>=d&&focus.y<d+10)?' ff-day-selected':'';" +
                "html+='<button type=\"button\" class=\"ff-day'+sel+'\" data-ff-decade=\"'+d+'\">'+d+'</button>';}" +
                "if(cap)cap.textContent=d0+' – '+(d0+119);}" +
                "grid.innerHTML=html;grid.setAttribute('data-ff-month',focus.y+'-'+pad2(focus.m));p.setAttribute('data-ff-focused',ymd(focus));}" +
                "function rebuildGrid(p,focus){rebuildView(p,focus);}" +
                "function syncTimeSelects(p,dateObj){var h=p.querySelector('[data-ff-hours]');var m=p.querySelector('[data-ff-minutes]');var s=p.querySelector('[data-ff-seconds]');" +
                "if(h)h.value=String(dateObj.getHours());if(m)m.value=String(dateObj.getMinutes());if(s)s.value=String(dateObj.getSeconds());}" +
                "function readTime(p){var h=p.querySelector('[data-ff-hours]');var m=p.querySelector('[data-ff-minutes]');var s=p.querySelector('[data-ff-seconds]');" +
                "var hh=h?+h.value:0,mm=m?+m.value:0,ss=s?+s.value:0;var withSec=p.getAttribute('data-ff-seconds')==='true';" +
                "return pad2(hh)+':'+pad2(mm)+(withSec?(':'+pad2(ss)):'');}" +
                "function fmtDate(p,iso){var d=parseYmd(iso);if(!d)return iso;" +
                "try{return new Date(d.y,d.m-1,d.d).toLocaleDateString(p.getAttribute('data-ff-locale')||undefined,{month:'short',day:'numeric',year:'numeric'});}catch(e){return iso;}}" +
                "function formatDisplay(p,stored){if(!stored)return'';var kind=p.getAttribute('data-ff-picker-kind')||'date';" +
                "if(kind==='time')return stored;if(kind==='year')return stored;" +
                "if(kind==='month'){var mm=/^(\\d{4})-(\\d{2})/.exec(stored);if(mm){try{return new Date(+mm[1],+mm[2]-1,1).toLocaleDateString(p.getAttribute('data-ff-locale')||undefined,{month:'short',year:'numeric'});}catch(e){return stored;}}return stored;}" +
                "if(kind==='range'){var bits=stored.split(/\\s+to\\s+/);if(bits.length===2)return fmtDate(p,bits[0])+' – '+fmtDate(p,bits[1]);return stored;}" +
                "var d=parseYmd(stored);var timeMatch=/(\\d{2}:\\d{2}(?::\\d{2})?)/.exec(stored);var t=timeMatch?timeMatch[1]:'';" +
                "if(!d)return stored;var base=fmtDate(p,ymd(d));return(kind==='datetime'&&t)?base+' '+t:base;}" +
                "function setPickerValue(p,stored,focusOverride){p.setAttribute('data-ff-state',stored||'');" +
                "var hidden=p.querySelector('[data-ff-picker-value]');if(hidden)hidden.value=stored||'';" +
                "var display=p.querySelector('[data-ff-picker-display]');if(display)display.value=formatDisplay(p,stored||'');" +
                "var focus=focusOverride||parseYmd(stored)||parseYmd(p.getAttribute('data-ff-focused'))||{y:(new Date()).getFullYear(),m:(new Date()).getMonth()+1,d:1};" +
                "if(p.getAttribute('data-ff-date')==='true')rebuildView(p,focus);}" +
                "function composeValue(p,dayIso){var hasDate=p.getAttribute('data-ff-date')==='true';var hasTime=p.getAttribute('data-ff-time')==='true';" +
                "var kind=p.getAttribute('data-ff-picker-kind')||'date';" +
                "if(kind==='month'){var d=parseYmd(dayIso);return d?(d.y+'-'+pad2(d.m)):dayIso;}" +
                "if(kind==='year'){var d2=parseYmd(dayIso);return d2?String(d2.y):(dayIso||'');}" +
                "if(!hasDate&&hasTime)return readTime(p);if(hasDate&&!hasTime)return dayIso||'';" +
                "var day=dayIso||(parseYmd(p.getAttribute('data-ff-state')||'')?ymd(parseYmd(p.getAttribute('data-ff-state'))):null);" +
                "if(!day)day=ymd({y:(new Date()).getFullYear(),m:(new Date()).getMonth()+1,d:(new Date()).getDate()});" +
                "return day+' '+readTime(p);}" +
                "apply(pref());" +
                "media.addEventListener('change',function(){if(pref()==='system')apply('system');});" +
                "var searchTimers={};" +
                "document.addEventListener('input',function(ev){var el=ev.target;if(!el||!el.matches||!el.matches('.ff-table-search'))return;" +
                "var form=el.closest('form[data-ff-table-nav]');if(!form)return;var id=ffTableIndex(form.closest('.ff-table-wrap'));" +
                "clearTimeout(searchTimers[id]);searchTimers[id]=setTimeout(function(){" +
                "if(form.requestSubmit)form.requestSubmit();else form.dispatchEvent(new Event('submit',{cancelable:true,bubbles:true}));},280);});" +
                "var dragState=null;" +
                "document.addEventListener('pointerdown',function(ev){var handle=ev.target&&ev.target.closest&&ev.target.closest('[data-ff-dialog-drag-handle]');" +
                "if(!handle||ev.target.closest('[data-ff-dialog-close]'))return;var win=handle.closest('.ff-dialog-window');if(!win)return;" +
                "var rect=win.getBoundingClientRect();dragState={win:win,ox:ev.clientX-rect.left,oy:ev.clientY-rect.top};" +
                "win.style.position='fixed';win.style.margin='0';win.style.left=rect.left+'px';win.style.top=rect.top+'px';" +
                "win.setPointerCapture&&win.setPointerCapture(ev.pointerId);ev.preventDefault();});" +
                "document.addEventListener('pointermove',function(ev){if(!dragState)return;" +
                "dragState.win.style.left=(ev.clientX-dragState.ox)+'px';dragState.win.style.top=(ev.clientY-dragState.oy)+'px';});" +
                "document.addEventListener('pointerup',function(){dragState=null;});" +
                "document.addEventListener('click',function(ev){" +
                "var t=ev.target;if(!t||!t.closest)return;" +
                "if(t.closest('[data-ff-theme-toggle]')){var o=['light','dark','system'];" +
                "var n=o[(o.indexOf(pref())+1)%o.length];try{localStorage.setItem('ff-theme',n);}catch(e){}apply(n);return;}" +
                "var toggle=t.closest('[data-ff-toggle]');" +
                "if(toggle){var on=toggle.getAttribute('aria-pressed')!=='true';" +
                "toggle.setAttribute('aria-pressed',on?'true':'false');" +
                "toggle.setAttribute('aria-checked',on?'true':'false');" +
                "var wrap=toggle.closest('.ff-toggle-wrap')||toggle.parentElement;" +
                "var input=wrap&&wrap.querySelector('[data-ff-toggle-value]');" +
                "if(input)input.value=on?'1':'0';" +
                "var icon=toggle.querySelector('.ff-toggle-icon');var ic=on?toggle.getAttribute('data-ff-on-icon'):toggle.getAttribute('data-ff-off-icon');" +
                "if(icon&&ic)icon.setAttribute('data-ff-icon',ic);return;}" +
                "var picker=t.closest('[data-ff-picker]');" +
                "if(picker){" +
                "if(t.closest('[data-ff-picker-open]')&&!t.closest('[data-ff-picker-panel]')){" +
                "if(picker.classList.contains('ff-picker-open'))closePickers();else openPicker(picker);return;}" +
                "if(t.closest('[data-ff-picker-view-up]')){var vv=pickerView(picker);var next=vv==='days'?'months':vv==='months'?'years':vv==='years'?'decades':'decades';" +
                "var level=picker.getAttribute('data-ff-pick-level')||'day';if(level==='month'&&next==='days')next='months';" +
                "if(level==='year'&&(next==='days'||next==='months'))next='years';setPickerView(picker,next);" +
                "rebuildView(picker,parseYmd(picker.getAttribute('data-ff-focused'))||{y:(new Date()).getFullYear(),m:(new Date()).getMonth()+1,d:1});return;}" +
                "if(t.closest('[data-ff-picker-prev]')||t.closest('[data-ff-picker-next]')){" +
                "var dir=t.closest('[data-ff-picker-prev]')?-1:1;var f=parseYmd(picker.getAttribute('data-ff-focused'))||{y:(new Date()).getFullYear(),m:(new Date()).getMonth()+1,d:1};" +
                "var view=pickerView(picker);var nf=f;if(view==='days')nf=addMonths(f,dir);else if(view==='months')nf={y:f.y+dir,m:f.m,d:1};" +
                "else if(view==='years')nf={y:f.y+dir*12,m:f.m,d:1};else nf={y:f.y+dir*100,m:f.m,d:1};rebuildView(picker,nf);return;}" +
                "if(t.closest('[data-ff-picker-ok]')){closePickers();return;}" +
                "if(t.closest('[data-ff-picker-clear]')){setPickerValue(picker,'');closePickers();return;}" +
                "if(t.closest('[data-ff-picker-today]')){var now=new Date();var iso=now.getFullYear()+'-'+pad2(now.getMonth()+1)+'-'+pad2(now.getDate());" +
                "var focusNow={y:now.getFullYear(),m:now.getMonth()+1,d:now.getDate()};setPickerView(picker,picker.getAttribute('data-ff-pick-level')==='month'?'months':picker.getAttribute('data-ff-pick-level')==='year'?'years':'days');" +
                "if(picker.getAttribute('data-ff-time')==='true')syncTimeSelects(picker,now);" +
                "if(picker.getAttribute('data-ff-picker-kind')==='range'){setPickerValue(picker,iso+' to '+iso,focusNow);}else{setPickerValue(picker,composeValue(picker,iso),focusNow);}" +
                "if(picker.getAttribute('data-ff-time')!=='true')closePickers();return;}" +
                // Only match day/month/year *buttons* — the grid also has data-ff-month for focus (#313).
                "var monthBtn=t.closest('button[data-ff-month]');if(monthBtn){var fm=parseYmd(picker.getAttribute('data-ff-focused'))||{y:(new Date()).getFullYear(),m:1,d:1};" +
                "fm.m=+monthBtn.getAttribute('data-ff-month');fm.d=1;if(picker.getAttribute('data-ff-picker-kind')==='month'){setPickerValue(picker,fm.y+'-'+pad2(fm.m),fm);closePickers();}" +
                "else{setPickerView(picker,'days');rebuildView(picker,fm);}return;}" +
                "var yearBtn=t.closest('button[data-ff-year]');if(yearBtn){var fy={y:+yearBtn.getAttribute('data-ff-year'),m:1,d:1};" +
                "if(picker.getAttribute('data-ff-picker-kind')==='year'){setPickerValue(picker,String(fy.y),fy);closePickers();}" +
                "else{setPickerView(picker,'months');rebuildView(picker,fy);}return;}" +
                "var decadeBtn=t.closest('button[data-ff-decade]');if(decadeBtn){var fd={y:+decadeBtn.getAttribute('data-ff-decade'),m:1,d:1};setPickerView(picker,'years');rebuildView(picker,fd);return;}" +
                "var dayBtn=t.closest('button[data-ff-day]');" +
                "if(dayBtn&&!dayBtn.disabled&&dayBtn.getAttribute('data-ff-day')){var day=dayBtn.getAttribute('data-ff-day');var kind=picker.getAttribute('data-ff-picker-kind');" +
                "if(kind==='range'){var st=picker.getAttribute('data-ff-state')||'';var parts=st.split(/\\s+to\\s+/);" +
                "if(!st||parts.length!==2||(parts[0]&&parts[1]&&parts[0]!==parts[1])){setPickerValue(picker,day+' to '+day);}" +
                "else{var a=parts[0].slice(0,10);var b=day;if(b<a){var tmp=a;a=b;b=tmp;}setPickerValue(picker,a+' to '+b);" +
                "if(a!==b)closePickers();}}" +
                "else{setPickerValue(picker,composeValue(picker,day));" +
                "if(picker.getAttribute('data-ff-time')!=='true')closePickers();}" +
                "return;}" +
                "return;}" +
                "var openBtn=t.closest('[data-ff-dialog-open]');" +
                "if(openBtn){var mount=openBtn.closest('[data-ff-dialog-mount]');" +
                "if(mount){openDialog(mount.querySelector('[data-ff-dialog-panel]'));}return;}" +
                "if(t.closest('[data-ff-dialog-close]')||t.closest('.ff-dialog-overlay')){" +
                "var dm=t.closest('[data-ff-dialog-mount]');if(dm){closeDialog(dm.querySelector('[data-ff-dialog-panel]'));}return;}" +
                // Outside the picker (including elsewhere in a dialog) closes the calendar popup.
                "if(!t.closest('[data-ff-picker]'))closePickers();" +
                "if(t.closest('[data-ff-dialog-panel]'))return;" +
                "var tr=t.closest('[data-ff-menu-trigger]');" +
                "if(tr){var menu=tr.closest('[data-ff-menu]');var was=menu.classList.contains('ff-open');" +
                "closeMenus();if(!was)menu.classList.add('ff-open');return;}" +
                "if(t.closest('[data-ff-menu-panel]'))return;" +
                "if(t.closest('[data-ff-sidebar-toggle]')){root.classList.add('ff-sidebar-open');return;}" +
                "if(t.closest('[data-ff-sidebar-close]')||t.closest('[data-ff-backdrop]')){root.classList.remove('ff-sidebar-open');closeMenus();closeDialogs();closePickers();return;}" +
                "var nav=t.closest('a[data-ff-table-nav]');" +
                "if(nav){ev.preventDefault();ffTableNav(nav.getAttribute('href'),nav.closest('.ff-table-wrap'));return;}" +
                "closeMenus();closeDialogs();closePickers();});" +
                "function ffTableIndex(wrap){return Array.prototype.indexOf.call(document.querySelectorAll('.ff-table-wrap'),wrap);}" +
                "function ffTableNav(href,wrap,restoreSearch){if(!href)return;var idx=wrap?ffTableIndex(wrap):-1;" +
                "var live=wrap&&wrap.querySelector('.ff-table-search');" +
                "var keep=restoreSearch||(live?{v:live.value,s:live.selectionStart,e:live.selectionEnd}:null);" +
                "fetch(href,{credentials:'same-origin',headers:{'X-Requested-With':'FF-Table'}}).then(function(r){return r.text();}).then(function(html){" +
                "var doc=new DOMParser().parseFromString(html,'text/html');" +
                "var next=idx>=0?doc.querySelectorAll('.ff-table-wrap')[idx]:doc.querySelector('.ff-table-wrap');" +
                "var cur=idx>=0?document.querySelectorAll('.ff-table-wrap')[idx]:(wrap||document.querySelector('.ff-table-wrap'));" +
                "if(next&&cur){cur.replaceWith(next);" +
                "if(keep){var ns=(idx>=0?document.querySelectorAll('.ff-table-wrap')[idx]:document.querySelector('.ff-table-wrap'));" +
                "ns=ns&&ns.querySelector('.ff-table-search');if(ns){ns.focus();if(typeof keep.v==='string')ns.value=keep.v;" +
                "try{var end=typeof keep.e==='number'?keep.e:ns.value.length;var start=typeof keep.s==='number'?keep.s:end;ns.setSelectionRange(start,end);}catch(e){}}}}" +
                "try{history.pushState({},'',href);}catch(e){}" +
                "}).catch(function(){location.href=href;});}" +
                "document.addEventListener('submit',function(ev){var f=ev.target;if(!f||!f.matches||!f.matches('form[data-ff-table-nav]'))return;" +
                "ev.preventDefault();var wrap=f.closest('.ff-table-wrap');var action=f.getAttribute('action')||location.pathname;" +
                "var search=f.querySelector('.ff-table-search');var keep=search?{v:search.value,s:search.selectionStart,e:search.selectionEnd}:null;" +
                "var qs=new URLSearchParams(new FormData(f)).toString();ffTableNav(action+(qs?('?'+qs):''),wrap,keep);});" +
                "document.addEventListener('change',function(ev){var el=ev.target;if(!el||!el.matches)return;" +
                "if(el.matches('[data-ff-hours],[data-ff-minutes],[data-ff-seconds]')){var pk=el.closest('[data-ff-picker]');" +
                "if(pk){var dayIso=null;var st=pk.getAttribute('data-ff-state')||'';var d=parseYmd(st);if(d)dayIso=ymd(d);" +
                "setPickerValue(pk,composeValue(pk,dayIso));}return;}" +
                "if(!el.matches('[data-ff-table-nav-select]'))return;" +
                "var wrap=el.closest('.ff-table-wrap');var form=el.closest('form[data-ff-table-nav]');" +
                "if(form){if(form.requestSubmit)form.requestSubmit();else form.submit();return;}" +
                "var url=new URL(location.href);url.searchParams.set(el.name||'perPage',el.value);url.searchParams.set('page','1');" +
                "ffTableNav(url.pathname+url.search,wrap);});" +
                "document.addEventListener('keydown',function(ev){if(ev.key==='Escape'){" +
                "root.classList.remove('ff-sidebar-open');closeMenus();closeDialogs();closePickers();}});" +
                "})();</script>"
    }
}

class Panel {
    private var theme: FireflyTheme = KonaTheme()
    private var layout: PanelLayout = DefaultLayout()
    private val hooks = RenderHooks()
    private val overrides = linkedMapOf<String, String>()
    private var csrfToken: String? = null
    private var documentTitle: String = "Firefly"
    private var brandName: String = "Firefly"
    private var mode: ChromeMode = ChromeMode.PANEL
    private var maxWidth: String? = null

    fun theme(theme: FireflyTheme): Panel = apply { this.theme = theme }

    fun layout(layout: PanelLayout): Panel = apply { this.layout = layout }

    fun hook(hook: Hook, html: String): Panel = apply { hooks.add(hook, html) }

    fun overridePartial(slot: String, html: String): Panel = apply { overrides[slot] = html }

    fun csrfToken(token: String?): Panel = apply { csrfToken = token }

    fun documentTitle(title: String): Panel = apply { documentTitle = title }

    fun brandName(name: String): Panel = apply { brandName = name }

    fun mode(mode: ChromeMode): Panel = apply { this.mode = mode }

    /**
     * Default content max-width for pages and resources in this chrome
     * (`xs`…`7xl`, `full`, `screen`, `prose`). Emits `data-ff-max-width` on `.ff-panel`.
     */
    fun maxWidth(width: String): Panel = apply {
        this.maxWidth = MaxWidth.requireKnown(width)
    }

    fun render(): String {
        val partials = linkedMapOf<String, String>()
        val slots = if (mode == ChromeMode.AUTH) listOf("body") else SLOTS
        for (slot in slots) {
            val fromApp = overrides[slot]
            val fromTheme = theme.partial(slot)
            val chosen = when {
                fromApp != null -> fromApp
                fromTheme != null -> fromTheme
                else -> builtin(slot)
            }
            val missing = requiredMarkers(slot).filter { marker -> !chosen.contains(marker) }
            if (missing.isNotEmpty()) {
                throw FireflyException("Partial [$slot] is missing markers $missing")
            }
            partials[slot] = fill(slot, chosen)
        }
        return layout.render(
            ChromeContext(
                theme,
                hooks,
                partials,
                Stylesheet.render(theme),
                csrfToken,
                documentTitle,
                brandName,
                mode,
                maxWidth,
            ),
        )
    }

    private fun fill(slot: String, template: String): String {
        var html = template
        for (hook in hooksFor(slot)) {
            html = html.replace("<!-- ff-hook:${hook.marker} -->", hooks.render(hook))
        }
        return html
    }

    companion object {
        private val SLOTS = listOf("sidebar", "topbar", "body", "footer")
    }
}

internal fun hooksFor(slot: String): List<Hook> = when (slot) {
    "sidebar" -> listOf(Hook.SIDEBAR_START, Hook.SIDEBAR_END)
    "topbar" -> listOf(Hook.TOPBAR_START, Hook.TOPBAR_END)
    "body" -> listOf(Hook.BODY_START, Hook.BODY_END)
    "footer" -> listOf(Hook.FOOTER)
    else -> emptyList()
}

internal fun requiredMarkers(slot: String): List<String> = hooksFor(slot).map { "<!-- ff-hook:${it.marker} -->" }

private const val SIDEBAR = """<aside class="ff-sidebar"><!-- ff-hook:sidebar.start --><!-- ff-hook:sidebar.end --></aside>"""
private const val TOPBAR = """<header class="ff-topbar"><!-- ff-hook:topbar.start --><!-- ff-hook:topbar.end --></header>"""
private const val BODY = """<main class="ff-body"><!-- ff-hook:body.start --><!-- ff-hook:body.end --></main>"""
private const val FOOTER = """<footer class="ff-footer"><!-- ff-hook:footer --></footer>"""

internal fun builtin(slot: String): String = when (slot) {
    "sidebar" -> SIDEBAR
    "topbar" -> TOPBAR
    "body" -> BODY
    "footer" -> FOOTER
    else -> throw FireflyException("Unknown partial [$slot]")
}
