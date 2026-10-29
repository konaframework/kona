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
                "chromeScript" to CHROME_SCRIPT + FILTERS_SCRIPT + WIDGETS_SCRIPT,
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
         * dialogs (`data-ff-dialog-*`), Select combobox (`data-ff-combobox`), date
         * pickers, table soft-nav (`data-ff-table-nav`) that swaps
         * `.ff-table-wrap` via fetch without a full document reload,
         * processing/loading buttons on mutate submits, toast auto-dismiss, and
         * table bulk selection (`data-ff-select-*`, `data-ff-bulk-*`).
         * Alpine is optional — dialogs, combobox, and table nav open without it.
         */
        /** Table filter panels, live filters, and the query builder (`data-ff-filters-*`, `data-ff-qb-*`). */
        internal val FILTERS_SCRIPT: String by lazy {
            "<script>" + Stylesheet.readCss("/org/konaframework/firefly/support/table-filters.js").trim() + "</script>"
        }

        internal val WIDGETS_SCRIPT: String by lazy {
            "<script>" + Stylesheet.readCss("/org/konaframework/firefly/support/form-widgets.js").trim() + "</script>"
        }

        internal const val CHROME_SCRIPT: String =
            "<script>(function(){" +
                "var root=document.querySelector('.ff-panel');if(!root)return;" +
                "var media=window.matchMedia('(prefers-color-scheme: dark)');" +
                "function spinnerHtml(){return '<span class=\"ff-spinner\" aria-hidden=\"true\"></span>';}" +
                "function markLoading(el){if(!el||el.getAttribute('data-ff-loading')==='true')return false;" +
                "el.setAttribute('data-ff-loading','true');el.setAttribute('aria-busy','true');" +
                "if('disabled' in el)el.disabled=true;else{el.setAttribute('aria-disabled','true');}" +
                "el.classList.add('ff-loading');" +
                "if(!el.querySelector(':scope > .ff-spinner'))el.insertAdjacentHTML('afterbegin',spinnerHtml());" +
                "return true;}" +
                "function isExcludedTrigger(el){return !!(el.closest('[data-ff-dialog-switch],[data-ff-dialog-close]')||" +
                "el.classList.contains('ff-action-cancel')||el.matches('a.ff-action-submit,a.ff-action-trigger'));}" +
                "function isProcessingTrigger(el){if(!el||isExcludedTrigger(el))return false;" +
                "if(el.matches('button.ff-action-submit,button.ff-action-trigger[type=\"submit\"],button.ff-action-trigger[form],'+" +
                "'button[type=\"submit\"]:not(.ff-menu-item):not(.ff-action-cancel):not(.ff-action-outlined)'))return true;" +
                "if(el.matches('button.ff-action-submit,button.ff-action-trigger')&&(el.getAttribute('wire:click')||" +
                "(el.getAttribute('x-data')||'').indexOf('ffAction')>=0))return true;" +
                "return false;}" +
                "function isMutatingForm(f){return !!(f&&f.matches&&(f.matches('.ff-action-form,.ff-delete-form,.ff-action-post,form#ff-form')||" +
                "f.querySelector('.ff-action-schema')));}" +
                "function armNotifications(){var stack=root.querySelector('[data-ff-notifications]');if(!stack)return;" +
                "stack.querySelectorAll('.ff-notification').forEach(function(el){" +
                "if(el.getAttribute('data-ff-toast-armed')==='true')return;el.setAttribute('data-ff-toast-armed','true');" +
                "el.classList.add('ff-notification-enter');" +
                "requestAnimationFrame(function(){el.classList.add('ff-notification-show');});" +
                "function dismiss(){if(el.getAttribute('data-ff-leaving')==='true')return;" +
                "el.setAttribute('data-ff-leaving','true');el.classList.remove('ff-notification-show');" +
                "el.classList.add('ff-notification-leave');" +
                "setTimeout(function(){if(el.parentNode)el.parentNode.removeChild(el);" +
                "if(stack&&!stack.querySelector('.ff-notification'))stack.remove();},220);}" +
                "var btn=el.querySelector('[data-ff-notification-dismiss]');if(btn)btn.addEventListener('click',dismiss);" +
                "var secs=+(el.getAttribute('data-ff-duration')||'6');" +
                "if(secs>0){var t=setTimeout(dismiss,secs*1000);el.addEventListener('mouseenter',function(){clearTimeout(t);}, {once:true});}" +
                "});}" +
                "armNotifications();" +
                "document.addEventListener('click',function(ev){var t=ev.target;if(!t||!t.closest)return;" +
                "var btn=t.closest('button');if(!btn||!isProcessingTrigger(btn))return;" +
                "if(btn.type==='submit'||btn.getAttribute('type')==='submit'||btn.hasAttribute('form'))return;" +
                "if(btn.getAttribute('data-ff-loading')==='true'){ev.preventDefault();ev.stopPropagation();return;}" +
                "markLoading(btn);},true);" +
                "document.addEventListener('submit',function(ev){var f=ev.target;if(!f||!f.matches||!f.matches('form'))return;" +
                "if(f.matches('form[data-ff-table-nav]'))return;" +
                "var btn=ev.submitter||f.querySelector('button[type=\"submit\"],button.ff-action-submit,button.ff-action-trigger[type=\"submit\"]');" +
                "if(btn&&btn.getAttribute('data-ff-loading')==='true'){ev.preventDefault();return;}" +
                "if(!isMutatingForm(f)&&!(btn&&isProcessingTrigger(btn)))return;" +
                "if(btn&&isExcludedTrigger(btn))return;" +
                "if(btn){if(!markLoading(btn)){ev.preventDefault();}}" +
                "},true);" +
                "function pref(){try{var v=localStorage.getItem('ff-theme');" +
                "return v==='light'||v==='dark'||v==='system'?v:'system';}catch(e){return 'system';}}" +
                "function apply(p){var d=p==='dark'||(p!=='light'&&media.matches);" +
                "document.documentElement.classList.toggle('ff-dark',d);" +
                "root.querySelectorAll('[data-ff-theme-toggle]').forEach(function(b){b.setAttribute('data-ff-theme',p);});}" +
                "function clearMenuPanel(panel){if(!panel)return;" +
                "panel.style.position='';panel.style.top='';panel.style.left='';panel.style.right='';" +
                "panel.style.width='';panel.style.minWidth='';panel.style.maxWidth='';panel.style.zIndex='';" +
                "panel.style.display='';panel.style.visibility='';}" +
                "function closeMenus(){root.querySelectorAll('[data-ff-menu].ff-open').forEach(function(m){" +
                "m.classList.remove('ff-open');clearMenuPanel(m.querySelector('[data-ff-menu-panel]'));});}" +
                "function positionRowMenu(menu){if(!menu||!menu.classList.contains('ff-row-action-menu'))return;" +
                "var panel=menu.querySelector('[data-ff-menu-panel]');var trigger=menu.querySelector('[data-ff-menu-trigger]');" +
                "if(!panel||!trigger)return;var rect=trigger.getBoundingClientRect();var pad=8;var gap=4;" +
                "panel.style.position='fixed';panel.style.right='auto';panel.style.zIndex='1100';" +
                "panel.style.minWidth='10rem';" +
                "var width=Math.max(panel.offsetWidth||160,160);var height=panel.offsetHeight||0;" +
                "var top=rect.bottom+gap;if(top+height>window.innerHeight-pad){var above=rect.top-height-gap;if(above>=pad)top=above;}" +
                "var left=rect.right-width;left=Math.min(Math.max(pad,left),Math.max(pad,window.innerWidth-width-pad));" +
                "panel.style.top=Math.round(top)+'px';panel.style.left=Math.round(left)+'px';" +
                "panel.style.width=Math.round(width)+'px';}" +
                "function resetDialogForms(panel){if(!panel)return;" +
                "panel.querySelectorAll('form').forEach(function(f){try{f.reset();}catch(e){}});" +
                "panel.querySelectorAll('[data-ff-toggle]').forEach(function(btn){" +
                "var wrap=btn.closest('.ff-toggle-wrap')||btn.parentElement;" +
                "var input=wrap&&wrap.querySelector('[data-ff-toggle-value]');" +
                "var on=!!(input&&input.value==='1');" +
                "btn.setAttribute('aria-pressed',on?'true':'false');" +
                "btn.setAttribute('aria-checked',on?'true':'false');});" +
                "panel.querySelectorAll('[data-ff-combobox]').forEach(function(box){syncCombobox(box);});}" +
                // Drag sets position:fixed + margin:0 + left/top. Clear all of them on
                // close/open so the next open restores stylesheet centering (#316).
                "function setExpandIcon(win,on){var btn=win&&win.querySelector('[data-ff-dialog-expand]');if(!btn)return;" +
                "btn.setAttribute('aria-pressed',on?'true':'false');" +
                "btn.setAttribute('aria-label',on?'Restore':'Toggle fullscreen');" +
                "var path=btn.querySelector('path');if(!path)return;" +
                "path.setAttribute('d',on?'M9 9V4.5M9 9H4.5M9 9L3.75 3.75M9 15v4.5M9 15H4.5M9 15l-5.25 5.25M15 9h4.5M15 9V4.5M15 9l5.25-5.25M15 15h4.5M15 15v4.5m0-4.5l5.25 5.25':'M3.75 3.75v4.5m0-4.5h4.5m-4.5 0L9 9M3.75 20.25v-4.5m0 4.5h4.5m-4.5 0L9 15M20.25 3.75h-4.5m4.5 0v4.5m0-4.5L15 9m5.25 11.25h-4.5m4.5 0v-4.5m0 4.5L15 15');}" +
                "function resetDialogDrag(panel){if(!panel)return;" +
                "panel.querySelectorAll('.ff-dialog-window').forEach(function(dlg){" +
                "dlg.classList.remove('ff-dialog-expanded');setExpandIcon(dlg,false);" +
                "dlg.style.position='';dlg.style.margin='';dlg.style.left='';dlg.style.top='';dlg.style.width='';dlg.style.height='';dlg.style.maxWidth='';dlg.style.maxHeight='';dlg.style.transform='';});}" +
                "function closeDialog(panel){if(!panel)return;resetDialogForms(panel);resetDialogDrag(panel);" +
                "panel.classList.remove('ff-open');" +
                "panel.setAttribute('hidden','');panel.style.display='none';panel.setAttribute('aria-hidden','true');}" +
                "function closeDialogs(){root.querySelectorAll('[data-ff-dialog-panel].ff-open').forEach(closeDialog);}" +
                "function openDialog(panel){if(!panel)return;closeDialogs();" +
                "panel.classList.add('ff-open');" +
                "panel.removeAttribute('hidden');panel.style.display='block';panel.removeAttribute('aria-hidden');" +
                "resetDialogDrag(panel);}" +
                "function toggleDialogExpand(win){if(!win)return;var on=!win.classList.contains('ff-dialog-expanded');" +
                "win.classList.toggle('ff-dialog-expanded',on);setExpandIcon(win,on);" +
                "if(on){win.style.position='fixed';win.style.margin='0';win.style.left='1rem';win.style.top='1rem';" +
                "win.style.width='calc(100vw - 2rem)';win.style.height='calc(100dvh - 2rem)';win.style.maxWidth='none';win.style.maxHeight='none';}" +
                "else{win.style.position='';win.style.margin='';win.style.left='';win.style.top='';win.style.width='';win.style.height='';win.style.maxWidth='';win.style.maxHeight='';}}" +
                "function closePickers(except){root.querySelectorAll('[data-ff-picker].ff-picker-open').forEach(function(p){" +
                "if(except&&p===except)return;p.classList.remove('ff-picker-open');p.removeAttribute('data-ff-range-pending');p.removeAttribute('data-ff-range-hover');" +
                "var panel=p.querySelector('[data-ff-picker-panel]');if(panel){panel.setAttribute('hidden','');}});}" +
                "function openPicker(p){if(!p)return;closeComboboxes();closePickers(p);p.classList.add('ff-picker-open');" +
                "var panel=p.querySelector('[data-ff-picker-panel]');if(panel)panel.removeAttribute('hidden');" +
                "if(p.getAttribute('data-ff-date')==='true'){var lvl=p.getAttribute('data-ff-pick-level');" +
                "setPickerView(p,lvl==='month'?'months':lvl==='year'?'years':'days');" +
                "rebuildView(p,parseYmd(p.getAttribute('data-ff-focused'))||{y:(new Date()).getFullYear(),m:(new Date()).getMonth()+1,d:1});}}" +
                "function closeComboboxes(except){root.querySelectorAll('[data-ff-combobox].ff-combobox-open').forEach(function(c){" +
                "if(except&&c===except)return;c.classList.remove('ff-combobox-open');" +
                "var panel=c.querySelector('[data-ff-combobox-panel]');if(panel)panel.setAttribute('hidden','');" +
                "var display=c.querySelector('[data-ff-combobox-display]');if(display){display.setAttribute('aria-expanded','false');" +
                "if(c.getAttribute('data-ff-searchable')==='true'){" +
                "display.value=c.getAttribute('data-ff-multiple')==='true'?'':comboboxDisplay(c);}}});}" +
                "function openCombobox(c){if(!c)return;closePickers();closeComboboxes(c);c.classList.add('ff-combobox-open');" +
                "var panel=c.querySelector('[data-ff-combobox-panel]');if(panel)panel.removeAttribute('hidden');" +
                "var display=c.querySelector('[data-ff-combobox-display]');if(display){display.setAttribute('aria-expanded','true');" +
                "if(c.getAttribute('data-ff-searchable')==='true'){display.value='';display.focus();filterCombobox(c,'');}}}" +
                "function comboboxValues(c){var raw=c.getAttribute('data-ff-state')||'';if(!raw)return[];return raw.split(',').map(function(x){return x.trim();}).filter(Boolean);}" +
                "function comboboxLabelFor(c,value){var opt=c.querySelector('.ff-option[data-ff-value=\"'+cssEscape(value)+'\"]');" +
                "if(opt)return opt.getAttribute('data-ff-label')||'';" +
                "var chip=c.querySelector('.ff-combobox-chip[data-ff-value=\"'+cssEscape(value)+'\"]');" +
                "return chip?(chip.getAttribute('data-ff-label')||chip.textContent||value):value;}" +
                "function comboboxDisplay(c){var vals=comboboxValues(c);if(!vals.length)return'';" +
                "return vals.map(function(v){return comboboxLabelFor(c,v);}).join(', ');}" +
                "function renderComboboxChips(c){var wrap=c.querySelector('[data-ff-combobox-chips]');if(!wrap)return;" +
                "var vals=comboboxValues(c);wrap.innerHTML=vals.map(function(v){var label=comboboxLabelFor(c,v);" +
                "return '<span class=\"ff-combobox-chip\" data-ff-value=\"'+escAttr(v)+'\" data-ff-label=\"'+escAttr(label)+'\">'+" +
                "'<span class=\"ff-combobox-chip-label\">'+escHtml(label)+'</span>'+" +
                "'<button type=\"button\" class=\"ff-combobox-chip-remove\" data-ff-combobox-remove aria-label=\"Remove '+escAttr(label)+'\">&times;</button></span>';}).join('');}" +
                "function escAttr(s){return String(s).replace(/&/g,'&amp;').replace(/\"/g,'&quot;').replace(/</g,'&lt;');}" +
                "function escHtml(s){return String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');}" +
                "function cssEscape(s){try{return CSS.escape(s);}catch(e){return String(s).replace(/\\\\/g,'\\\\\\\\').replace(/\"/g,'\\\\\"');}}" +
                "function syncCombobox(c){var hidden=c.querySelector('[data-ff-combobox-value]');" +
                "var vals=comboboxValues(c);if(hidden)hidden.value=vals.join(',');" +
                "var multi=c.getAttribute('data-ff-multiple')==='true';" +
                "var display=c.querySelector('[data-ff-combobox-display]');" +
                "if(display&&!c.classList.contains('ff-combobox-open'))display.value=multi?'':comboboxDisplay(c);" +
                "if(multi)renderComboboxChips(c);" +
                "c.querySelectorAll('.ff-option').forEach(function(li){var on=vals.indexOf(li.getAttribute('data-ff-value')||'')>=0;" +
                "li.classList.toggle('ff-selected',on);li.setAttribute('aria-selected',on?'true':'false');});}" +
                "function setComboboxValue(c,value,label){var multi=c.getAttribute('data-ff-multiple')==='true';var vals=comboboxValues(c);" +
                "if(multi){var i=vals.indexOf(value);if(i>=0)vals.splice(i,1);else{var max=+(c.getAttribute('data-ff-max-items')||'0');" +
                "if(max>0&&vals.length>=max)return;vals.push(value);if(label){var opt=c.querySelector('.ff-option[data-ff-value=\"'+cssEscape(value)+'\"]');" +
                "if(opt)opt.setAttribute('data-ff-label',label);}}}" +
                "else{vals=value?[value]:[];}" +
                "c.setAttribute('data-ff-state',vals.join(','));syncCombobox(c);if(!multi)closeComboboxes();}" +
                "function formOptionsUrl(field,query){var path=location.pathname.replace(/\\/$/,'');" +
                "path=path.replace(/\\/(manage|create|edit|list|view)$/,'');" +
                "return path+'/form-options?field='+encodeURIComponent(field||'')+'&q='+encodeURIComponent(query||'');}" +
                "function optionCheckSvg(){return '<svg class=\"ff-icon\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" aria-hidden=\"true\"><path d=\"M4.5 12.75l6 6 9-13.5\"/></svg>';}" +
                "function renderComboboxOptions(c,items){var list=c.querySelector('[data-ff-combobox-options]');if(!list)return;" +
                "var vals=comboboxValues(c);var html=(items||[]).map(function(it){var value=String(it.value==null?'':it.value);var label=String(it.label==null?'':it.label);" +
                "var on=vals.indexOf(value)>=0;var enabled=it.enabled!==false;var css=on?'ff-option ff-selected':'ff-option';" +
                "return '<li class=\"'+css+'\" role=\"option\" aria-selected=\"'+(on?'true':'false')+'\" data-ff-value=\"'+escAttr(value)+'\" data-ff-label=\"'+escAttr(label)+'\"'+" +
                "(enabled?'':' data-ff-enabled=\"false\"')+'><span class=\"ff-option-text\">'+escHtml(label)+'</span><span class=\"ff-option-check\" aria-hidden=\"true\">'+optionCheckSvg()+'</span></li>';}).join('');" +
                "list.innerHTML=html;list.style.display=html?'':'none';" +
                "var empty=c.querySelector('[data-ff-combobox-empty]');if(empty){if(html)empty.setAttribute('hidden','');else empty.removeAttribute('hidden');}" +
                "syncCombobox(c);}" +
                "function remoteFilterCombobox(c,query){var field=c.getAttribute('data-ff-path')||'';var empty=c.querySelector('[data-ff-combobox-empty]');" +
                "var list=c.querySelector('[data-ff-combobox-options]');var searching=c.getAttribute('data-ff-searching')||'Searching';" +
                "if(empty){empty.textContent=searching;empty.removeAttribute('hidden');}if(list)list.style.display='none';" +
                "var seq=(+(c.getAttribute('data-ff-search-seq')||'0'))+1;c.setAttribute('data-ff-search-seq',String(seq));" +
                "var su=c.getAttribute('data-ff-search-url');" +
                "var surl=su?(su+(su.indexOf('?')<0?'?':'&')+'q='+encodeURIComponent(query||'')):formOptionsUrl(field,query);" +
                "fetch(surl,{credentials:'same-origin',headers:{'Accept':'application/json','X-Requested-With':'FF-Select'}})" +
                ".then(function(r){return r.json();}).then(function(items){if(String(seq)!==c.getAttribute('data-ff-search-seq'))return;" +
                "if(empty)empty.textContent=c.getAttribute('data-ff-empty')||'No results found';renderComboboxOptions(c,items);})" +
                ".catch(function(){if(String(seq)!==c.getAttribute('data-ff-search-seq'))return;" +
                "if(empty){empty.textContent=c.getAttribute('data-ff-empty')||'No results found';empty.removeAttribute('hidden');}if(list){list.innerHTML='';list.style.display='none';}});}" +
                "function filterCombobox(c,query){if(c.getAttribute('data-ff-relationship')||c.getAttribute('data-ff-search-url')){remoteFilterCombobox(c,query);return;}" +
                "var needle=(query||'').toLowerCase();var any=false;" +
                "c.querySelectorAll('.ff-option').forEach(function(li){var label=(li.getAttribute('data-ff-label')||'').toLowerCase();" +
                "var value=(li.getAttribute('data-ff-value')||'').toLowerCase();" +
                "var show=!needle||label.indexOf(needle)>=0||value.indexOf(needle)>=0;li.style.display=show?'':'none';if(show)any=true;});" +
                "var empty=c.querySelector('[data-ff-combobox-empty]');if(empty){if(any)empty.setAttribute('hidden','');else empty.removeAttribute('hidden');}" +
                "var list=c.querySelector('[data-ff-combobox-options]');if(list)list.style.display=any?'':'none';}" +

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
                "function rangeBounds(p){var pend=p.getAttribute('data-ff-range-pending');" +
                "if(pend){var hv=p.getAttribute('data-ff-range-hover')||pend;return hv<pend?{s:hv,e:pend,preview:true}:{s:pend,e:hv,preview:true};}" +
                "var bits=(p.getAttribute('data-ff-state')||'').split(/\\s+to\\s+|\\s+–\\s+|\\s+-\\s+|\\//);" +
                "if(bits.length===2)return{s:bits[0].trim().slice(0,10),e:bits[1].trim().slice(0,10),preview:false};return{s:null,e:null,preview:false};}" +
                "function daysHtml(p,focus){var kind=p.getAttribute('data-ff-picker-kind')||'date';var firstDay=+(p.getAttribute('data-ff-first-day')||'1');" +
                "var dis=disabledSet(p);var html='';var rb=kind==='range'?rangeBounds(p):null;var selected=null;" +
                "if(!rb){selected=parseYmd(p.getAttribute('data-ff-state')||'');if(selected)selected=ymd(selected);}" +
                "var first=new Date(focus.y,focus.m-1,1);var dow=first.getDay();" +
                "var lead=firstDay===1?((dow+6)%7):dow;var cursor=new Date(focus.y,focus.m-1,1-lead);" +
                "for(var i=0;i<42;i++){var cy=cursor.getFullYear(),cm=cursor.getMonth()+1,cd=cursor.getDate();" +
                "var iso=cy+'-'+pad2(cm)+'-'+pad2(cd);var outside=cm!==focus.m;var disabled=!!dis[iso]||outOfBounds(p,iso);" +
                "cursor.setDate(cursor.getDate()+1);" +
                "if(rb&&outside){html+='<span class=\"ff-day ff-day-empty\" aria-hidden=\"true\"></span>';continue;}" +
                "var cls='ff-day';if(outside)cls+=' ff-day-outside';if(disabled)cls+=' ff-day-disabled';" +
                "if(rb){if(rb.s&&rb.e&&iso>rb.s&&iso<rb.e)cls+=rb.preview?' ff-range-preview':' ff-day-in-range';" +
                "if(iso===rb.s)cls+=' ff-day-selected ff-range-start';if(iso===rb.e)cls+=' ff-day-selected ff-range-end';}" +
                "else if(selected&&iso===selected)cls+=' ff-day-selected';" +
                "html+='<button type=\"button\" class=\"'+cls+'\" data-ff-day=\"'+iso+'\"'+(disabled?' disabled':'')+'>'+cd+'</button>';}" +
                "return html;}" +
                "function rebuildView(p,focus){var grid=p.querySelector('[data-ff-picker-grid]');if(!grid)return;" +
                "var view=pickerView(p);var kind=p.getAttribute('data-ff-picker-kind')||'date';var locale=p.getAttribute('data-ff-locale');" +
                "var weekdays=p.querySelector('[data-ff-picker-weekdays]');if(weekdays)weekdays.style.display=view==='days'?'grid':'none';" +
                "var cap=p.querySelector('[data-ff-picker-caption]');var html='';var state=p.getAttribute('data-ff-state')||'';" +
                "if(view==='days'){html=daysHtml(p,focus);grid.className='ff-picker-grid';if(cap)cap.textContent=monthName(focus.y,focus.m,locale);" +
                "var g2=p.querySelector('[data-ff-picker-grid-next]');if(g2){var nx=addMonths(focus,1);g2.innerHTML=daysHtml(p,nx);" +
                "var c2=p.querySelector('[data-ff-picker-caption-next]');if(c2)c2.textContent=monthName(nx.y,nx.m,locale);}}" +
                "else if(view==='months'){grid.className='ff-picker-grid ff-picker-grid-months';" +
                "for(var m=1;m<=12;m++){var sel=(kind==='month'?state===focus.y+'-'+pad2(m):focus.m===m)?' ff-day-selected':'';" +
                "html+='<button type=\"button\" class=\"ff-day'+sel+'\" data-ff-month=\"'+m+'\">'+monthShort(m,locale)+'</button>';}" +
                "if(cap)cap.textContent=String(focus.y);}" +
                "else if(view==='years'){grid.className='ff-picker-grid ff-picker-grid-years';var y0=Math.floor(focus.y/12)*12;" +
                "for(var y=y0;y<y0+12;y++){var sel=(kind==='year'?state===String(y):focus.y===y)?' ff-day-selected':'';" +
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
                "var hint=p.querySelector('[data-ff-range-hint]');if(hint)hint.textContent=stored?formatDisplay(p,stored):'Select a start date';" +
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
                // Bulk selection: keys live in sessionStorage per page + table so pagination soft-nav keeps them.
                "function selKey(w){return 'ff-sel:'+location.pathname+':'+ffTableIndex(w);}" +
                "function readSel(w){var raw=null;try{raw=sessionStorage.getItem(selKey(w));}catch(e){}" +
                "if(raw){try{var o=JSON.parse(raw);return{keys:Array.isArray(o.keys)?o.keys:[],all:!!o.all};}catch(e){}}" +
                "var keys=[];w.querySelectorAll('[data-ff-select-record]:checked').forEach(function(b){keys.push(b.value);});return{keys:keys,all:false};}" +
                "function writeSel(w,s){try{sessionStorage.setItem(selKey(w),JSON.stringify(s));}catch(e){}}" +
                "function clearSel(w){if(w)writeSel(w,{keys:[],all:false});}" +
                "function selBoxes(scope){return Array.prototype.filter.call(scope.querySelectorAll('[data-ff-select-record]'),function(b){return !b.disabled;});}" +
                "function groupBoxes(w,name){return selBoxes(w).filter(function(b){var r=b.closest('[data-ff-record]');return r&&r.getAttribute('data-ff-group')===name;});}" +
                "function syncHead(box,boxes,s){if(!box)return;var on=boxes.filter(function(b){return s.all||s.keys.indexOf(b.value)>=0;}).length;" +
                "box.checked=boxes.length>0&&on===boxes.length;box.indeterminate=on>0&&on<boxes.length;}" +
                "function syncSel(w){if(!w||!w.hasAttribute('data-ff-selectable'))return;var s=readSel(w);" +
                "w.querySelectorAll('[data-ff-select-record]').forEach(function(b){var c=!b.disabled&&(s.all||s.keys.indexOf(b.value)>=0);b.checked=c;" +
                "var r=b.closest('[data-ff-record]');if(r)r.classList.toggle('ff-selected',c);});" +
                "syncHead(w.querySelector('[data-ff-select-page]'),selBoxes(w),s);" +
                "w.querySelectorAll('[data-ff-select-group]').forEach(function(g){syncHead(g,groupBoxes(w,g.getAttribute('data-ff-select-group')),s);});" +
                "var total=+(w.getAttribute('data-ff-total')||'0');var count=s.all?total:s.keys.length;" +
                "w.querySelectorAll('[data-ff-bulk-count]').forEach(function(n){n.textContent=String(count);if(count)n.removeAttribute('hidden');else n.setAttribute('hidden','');});" +
                "w.querySelectorAll('[data-ff-bulk-trigger],[data-ff-bulk-run]').forEach(function(b){b.disabled=count===0||b.hasAttribute('data-ff-bulk-locked');});" +
                "w.querySelectorAll('[data-ff-requires-selection]').forEach(function(b){if(count===0||b.hasAttribute('data-ff-selection-locked'))b.setAttribute('disabled','');else b.removeAttribute('disabled');});" +
                "var bar=w.querySelector('[data-ff-selection]');if(bar){if(count)bar.removeAttribute('hidden');else bar.setAttribute('hidden','');" +
                "var label=bar.querySelector('[data-ff-selection-count]');if(label)label.textContent=count===1?'1 record selected':count+' records selected';" +
                "var all=bar.querySelector('[data-ff-select-all-matching]');if(all){if(!s.all&&count<total)all.removeAttribute('hidden');else all.setAttribute('hidden','');}}}" +
                "function toggleBoxes(w,boxes,s){var allOn=boxes.length>0&&boxes.every(function(b){return s.all||s.keys.indexOf(b.value)>=0;});" +
                "if(allOn){if(s.all){s.all=false;s.keys=selBoxes(w).filter(function(b){return boxes.indexOf(b)<0;}).map(function(b){return b.value;});}" +
                "else{boxes.forEach(function(b){var i=s.keys.indexOf(b.value);if(i>=0)s.keys.splice(i,1);});}}" +
                "else{var max=+(w.getAttribute('data-ff-max-selectable')||'0');" +
                "boxes.forEach(function(b){if(s.keys.indexOf(b.value)<0&&(max<=0||s.keys.length<max))s.keys.push(b.value);});}" +
                "writeSel(w,s);syncSel(w);}" +
                "function fillBulk(f,s){var k=f.querySelector('[data-ff-bulk-keys]');if(k)k.value=s.keys.join(',');" +
                "var a=f.querySelector('[data-ff-bulk-all]');if(a)a.value=s.all?'1':'';" +
                "if(!f.querySelector('input[name=_token]')){var m=document.querySelector('meta[name=csrf-token]');" +
                "if(m){var tk=document.createElement('input');tk.type='hidden';tk.name='_token';tk.value=m.getAttribute('content')||'';f.appendChild(tk);}}}" +
                "function bulkSubmit(f){var w=f.closest('.ff-table-wrap');if(!w)return;fillBulk(f,readSel(w));" +
                "if(f.getAttribute('data-ff-deselect')==='true')clearSel(w);}" +
                "apply(pref());" +
                "document.querySelectorAll('.ff-table-wrap[data-ff-selectable]').forEach(syncSel);" +
                "media.addEventListener('change',function(){if(pref()==='system')apply('system');});" +
                "var searchTimers={};var comboTimers={};" +
                "document.addEventListener('input',function(ev){var el=ev.target;if(!el||!el.matches)return;" +
                "if(el.matches('[data-ff-combobox-display]')){var box=el.closest('[data-ff-combobox]');if(!box)return;" +
                "if(!box.classList.contains('ff-combobox-open'))openCombobox(box);" +
                "var wait=+(box.getAttribute('data-ff-debounce')||'300');var key=box.getAttribute('data-ff-path')||'combo';" +
                "clearTimeout(comboTimers[key]);comboTimers[key]=setTimeout(function(){filterCombobox(box,el.value);},wait);return;}" +
                "if(!el.matches('.ff-table-search'))return;" +
                "var form=el.closest('form[data-ff-table-nav]');if(!form)return;var id=ffTableIndex(form.closest('.ff-table-wrap'));" +
                "clearTimeout(searchTimers[id]);searchTimers[id]=setTimeout(function(){" +
                "if(form.requestSubmit)form.requestSubmit();else form.dispatchEvent(new Event('submit',{cancelable:true,bubbles:true}));},280);});" +
                "var dragState=null;var resizeState=null;" +
                "document.addEventListener('pointerdown',function(ev){" +
                "var resize=ev.target&&ev.target.closest&&ev.target.closest('[data-ff-dialog-resize]');" +
                "if(resize){var rwin=resize.closest('.ff-dialog-window');if(!rwin||rwin.classList.contains('ff-dialog-expanded'))return;" +
                "var rr=rwin.getBoundingClientRect();resizeState={win:rwin,x:ev.clientX,y:ev.clientY,w:rr.width,h:rr.height};" +
                "rwin.style.position='fixed';rwin.style.margin='0';rwin.style.left=rr.left+'px';rwin.style.top=rr.top+'px';" +
                "rwin.style.width=rr.width+'px';rwin.style.height=rr.height+'px';rwin.style.maxWidth='none';rwin.style.maxHeight='none';" +
                "rwin.setPointerCapture&&rwin.setPointerCapture(ev.pointerId);ev.preventDefault();return;}" +
                "var handle=ev.target&&ev.target.closest&&ev.target.closest('[data-ff-dialog-drag-handle]');" +
                "if(!handle||ev.target.closest('[data-ff-dialog-close],[data-ff-dialog-expand]'))return;" +
                "var win=handle.closest('.ff-dialog-window');if(!win||win.classList.contains('ff-dialog-expanded'))return;" +
                "var rect=win.getBoundingClientRect();dragState={win:win,ox:ev.clientX-rect.left,oy:ev.clientY-rect.top};" +
                "win.style.position='fixed';win.style.margin='0';win.style.left=rect.left+'px';win.style.top=rect.top+'px';" +
                "win.setPointerCapture&&win.setPointerCapture(ev.pointerId);ev.preventDefault();});" +
                "document.addEventListener('pointermove',function(ev){" +
                "if(resizeState){var nw=Math.max(280,resizeState.w+(ev.clientX-resizeState.x));" +
                "var nh=Math.max(200,resizeState.h+(ev.clientY-resizeState.y));" +
                "resizeState.win.style.width=nw+'px';resizeState.win.style.height=nh+'px';return;}" +
                "if(!dragState)return;" +
                "dragState.win.style.left=(ev.clientX-dragState.ox)+'px';dragState.win.style.top=(ev.clientY-dragState.oy)+'px';});" +
                "document.addEventListener('pointerup',function(){dragState=null;resizeState=null;});" +
                "document.addEventListener('click',function(ev){" +
                "var t=ev.target;if(!t||!t.closest)return;" +
                // Relation manager tabs: switch panels in place and keep ?relation= in the URL.
                "var relTab=t.closest('[data-ff-relation-tab]');" +
                "if(relTab){var relBox=relTab.closest('[data-ff-relation-managers]');if(relBox){ev.preventDefault();" +
                "var relName=relTab.getAttribute('data-ff-relation-tab')||'';" +
                "relBox.querySelectorAll('[data-ff-relation-tab]').forEach(function(x){x.setAttribute('aria-selected',x===relTab?'true':'false');});" +
                "relBox.querySelectorAll('[data-ff-relation-panel]').forEach(function(p){if(p.getAttribute('data-ff-relation-panel')===relName)p.removeAttribute('hidden');else p.setAttribute('hidden','');});" +
                "try{var ru=new URL(location.href);ru.searchParams.set('relation',relName);history.replaceState({},'',ru.pathname+ru.search);}catch(e){}}return;}" +
                "var expandBtn=t.closest('[data-ff-dialog-expand]');" +
                "if(expandBtn){toggleDialogExpand(expandBtn.closest('.ff-dialog-window'));return;}" +
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
                "var combo=t.closest('[data-ff-combobox]');" +
                "if(combo){" +
                "var rm=t.closest('[data-ff-combobox-remove]');" +
                "if(rm){var chip=rm.closest('.ff-combobox-chip');" +
                "if(chip){setComboboxValue(combo,chip.getAttribute('data-ff-value')||'',chip.getAttribute('data-ff-label')||'');}return;}" +
                "var opt=t.closest('.ff-option');" +
                "if(opt&&opt.getAttribute('data-ff-enabled')!=='false'){" +
                "setComboboxValue(combo,opt.getAttribute('data-ff-value')||'',opt.getAttribute('data-ff-label')||'');return;}" +
                "if(t.closest('[data-ff-combobox-open]')&&!t.closest('[data-ff-combobox-panel]')){" +
                "if(combo.classList.contains('ff-combobox-open')){closeComboboxes();}else{openCombobox(combo);}return;}" +
                "return;}" +
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
                "if(kind==='range'){var pend=picker.getAttribute('data-ff-range-pending');var keep=parseYmd(picker.getAttribute('data-ff-focused'));" +
                "if(!pend){picker.setAttribute('data-ff-range-pending',day);picker.removeAttribute('data-ff-range-hover');" +
                "var rh=picker.querySelector('[data-ff-range-hint]');if(rh)rh.textContent='Select an end date';rebuildView(picker,keep||parseYmd(day));}" +
                "else{var a=pend;var b=day;if(b<a){var tmp=a;a=b;b=tmp;}picker.removeAttribute('data-ff-range-pending');picker.removeAttribute('data-ff-range-hover');" +
                "setPickerValue(picker,a+' to '+b,keep);closePickers();}}" +
                "else{setPickerValue(picker,composeValue(picker,day));" +
                "if(picker.getAttribute('data-ff-time')!=='true')closePickers();}" +
                "return;}" +
                "return;}" +
                // Whole-row click: navigate or trigger View/Edit (skip interactive children) (#377).
                "var recordRow=t.closest('[data-ff-record-url],[data-ff-record-action]');" +
                "if(recordRow&&!t.closest('a,button,input,label,select,textarea,summary,.ff-row-actions,.ff-table-select,[data-ff-menu],[data-ff-dialog-mount],[data-ff-column-toggle]')){" +
                "var recordUrl=recordRow.getAttribute('data-ff-record-url');" +
                "if(recordUrl){window.location.assign(recordUrl);return;}" +
                "var recordAction=recordRow.getAttribute('data-ff-record-action');" +
                "if(recordAction){var recordTrigger=recordRow.querySelector('[data-ff-dialog-open][data-ff-action=\"'+recordAction+'\"]')" +
                "||recordRow.querySelector('a[data-ff-action=\"'+recordAction+'\"]')" +
                "||recordRow.querySelector('[data-ff-action=\"'+recordAction+'\"]');" +
                "if(recordTrigger){recordTrigger.click();}return;}}" +
                // Bulk bar: select every matching record, clear, or run a bulk action on the selection.
                "if(t.closest('[data-ff-select-all-matching]')){var aw=t.closest('.ff-table-wrap');if(aw){writeSel(aw,{keys:[],all:true});syncSel(aw);}return;}" +
                "if(t.closest('[data-ff-deselect-all]')){var dw=t.closest('.ff-table-wrap');if(dw){clearSel(dw);syncSel(dw);}return;}" +
                "var bulkBtn=t.closest('[data-ff-bulk-run]');" +
                "if(bulkBtn){var bw=bulkBtn.closest('.ff-table-wrap');var bs=bw?readSel(bw):null;" +
                "if(bulkBtn.disabled||!bs||(!bs.all&&!bs.keys.length))return;" +
                "var bm=bw.querySelector('[data-ff-bulk-mount=\"'+cssEscape(bulkBtn.getAttribute('data-ff-bulk-run')||'')+'\"]');" +
                "var bf=bm&&bm.querySelector('form[data-ff-bulk-form]');if(!bf)return;fillBulk(bf,bs);closeMenus();" +
                "var bp=bm.querySelector('[data-ff-dialog-panel]');if(bp){openDialog(bp);return;}" +
                "bulkSubmit(bf);bf.submit();return;}" +
                // View→Edit (and similar): close current dialog and open another mount in the same row (#376).
                "var switchBtn=t.closest('[data-ff-dialog-switch]');" +
                "if(switchBtn){var switchName=switchBtn.getAttribute('data-ff-dialog-switch')||'';" +
                "var switchRow=switchBtn.closest('.ff-row-actions')||switchBtn.closest('tr');" +
                "if(switchRow&&switchName){var switchMount=null;" +
                "switchRow.querySelectorAll('[data-ff-dialog-mount]').forEach(function(m){" +
                "if(m.querySelector('[data-ff-dialog-open][data-ff-action=\"'+switchName+'\"]'))switchMount=m;});" +
                "if(switchMount)openDialog(switchMount.querySelector('[data-ff-dialog-panel]'));}return;}" +
                "var openBtn=t.closest('[data-ff-dialog-open]');" +
                "if(openBtn){var mount=openBtn.closest('[data-ff-dialog-mount]');" +
                "if(mount){openDialog(mount.querySelector('[data-ff-dialog-panel]'));}return;}" +
                "if(t.closest('[data-ff-dialog-close]')||t.closest('.ff-dialog-overlay')){" +
                "var dm=t.closest('[data-ff-dialog-mount]');if(dm){closeDialog(dm.querySelector('[data-ff-dialog-panel]'));}return;}" +
                // Outside the picker/combobox (including elsewhere in a dialog) closes popups.
                "if(!t.closest('[data-ff-picker]'))closePickers();" +
                "if(!t.closest('[data-ff-combobox]'))closeComboboxes();" +
                "if(t.closest('[data-ff-dialog-panel]'))return;" +
                "var tr=t.closest('[data-ff-menu-trigger]');" +
                "if(tr){var menu=tr.closest('[data-ff-menu]');var was=menu.classList.contains('ff-open');" +
                "closeMenus();if(!was){menu.classList.add('ff-open');positionRowMenu(menu);}return;}" +
                "if(t.closest('[data-ff-menu-panel]'))return;" +
                "if(t.closest('[data-ff-sidebar-toggle]')){root.classList.add('ff-sidebar-open');return;}" +
                "if(t.closest('[data-ff-sidebar-close]')||t.closest('[data-ff-backdrop]')){root.classList.remove('ff-sidebar-open');closeMenus();closeDialogs();closePickers();closeComboboxes();return;}" +
                "var nav=t.closest('a[data-ff-table-nav]');" +
                "if(nav){ev.preventDefault();var nw=nav.closest('.ff-table-wrap');" +
                "if(nw&&nav.hasAttribute('data-ff-filter-nav')&&nw.getAttribute('data-ff-deselect-on-filter')==='true')clearSel(nw);" +
                "ffTableNav(nav.getAttribute('href'),nw);return;}" +
                "closeMenus();closeDialogs();closePickers();closeComboboxes();});" +
                "function ffTableIndex(wrap){return Array.prototype.indexOf.call(document.querySelectorAll('.ff-table-wrap'),wrap);}" +
                "function ffTableNav(href,wrap,restoreSearch){if(!href)return;var idx=wrap?ffTableIndex(wrap):-1;" +
                "var live=wrap&&wrap.querySelector('.ff-table-search');" +
                "var keep=restoreSearch||(live?{v:live.value,s:live.selectionStart,e:live.selectionEnd}:null);" +
                "fetch(href,{credentials:'same-origin',headers:{'X-Requested-With':'FF-Table'}}).then(function(r){return r.text();}).then(function(html){" +
                "var doc=new DOMParser().parseFromString(html,'text/html');" +
                "var next=idx>=0?doc.querySelectorAll('.ff-table-wrap')[idx]:doc.querySelector('.ff-table-wrap');" +
                "var cur=idx>=0?document.querySelectorAll('.ff-table-wrap')[idx]:(wrap||document.querySelector('.ff-table-wrap'));" +
                "if(next&&cur){cur.replaceWith(next);syncSel(next);" +
                "if(keep){var ns=(idx>=0?document.querySelectorAll('.ff-table-wrap')[idx]:document.querySelector('.ff-table-wrap'));" +
                "ns=ns&&ns.querySelector('.ff-table-search');if(ns){ns.focus();if(typeof keep.v==='string')ns.value=keep.v;" +
                "try{var end=typeof keep.e==='number'?keep.e:ns.value.length;var start=typeof keep.s==='number'?keep.s:end;ns.setSelectionRange(start,end);}catch(e){}}}}" +
                "try{history.pushState({},'',href);}catch(e){}" +
                "}).catch(function(){location.href=href;});}" +
                "document.addEventListener('submit',function(ev){var f=ev.target;if(!f||!f.matches)return;" +
                "if(f.matches('form[data-ff-bulk-form]')){bulkSubmit(f);return;}" +
                "if(!f.matches('form[data-ff-table-nav]'))return;" +
                "ev.preventDefault();var wrap=f.closest('.ff-table-wrap');var action=f.getAttribute('action')||location.pathname;" +
                "if(wrap&&wrap.getAttribute('data-ff-deselect-on-filter')==='true')clearSel(wrap);" +
                "var search=f.querySelector('.ff-table-search');var keep=search?{v:search.value,s:search.selectionStart,e:search.selectionEnd}:null;" +
                "var qs=new URLSearchParams(new FormData(f)).toString();ffTableNav(action+(qs?('?'+qs):''),wrap,keep);});" +
                "document.addEventListener('change',function(ev){var el=ev.target;if(!el||!el.matches)return;" +
                "if(el.matches('[data-ff-select-record],[data-ff-select-page],[data-ff-select-group]')){var sw=el.closest('.ff-table-wrap');if(!sw)return;var ss=readSel(sw);" +
                "if(el.matches('[data-ff-select-page]')){toggleBoxes(sw,selBoxes(sw),ss);return;}" +
                "if(el.matches('[data-ff-select-group]')){var gb=groupBoxes(sw,el.getAttribute('data-ff-select-group'));" +
                "if(sw.getAttribute('data-ff-select-groups-only')==='true'&&!gb.every(function(b){return ss.keys.indexOf(b.value)>=0;}))ss={keys:[],all:false};" +
                "toggleBoxes(sw,gb,ss);return;}" +
                "if(ss.all){ss.all=false;ss.keys=selBoxes(sw).filter(function(b){return b.checked;}).map(function(b){return b.value;});writeSel(sw,ss);syncSel(sw);return;}" +
                "var si=ss.keys.indexOf(el.value);" +
                // Group-only: picking a row in another group replaces the selection before the max check.
                "if(el.checked&&si<0){if(sw.getAttribute('data-ff-select-groups-only')==='true'){var srow=el.closest('[data-ff-record]');var sg=srow?srow.getAttribute('data-ff-group'):null;" +
                "ss.keys=ss.keys.filter(function(k){var ob=selBoxes(sw).filter(function(b){return b.value===k;})[0];var orow=ob&&ob.closest('[data-ff-record]');" +
                "return !orow||orow.getAttribute('data-ff-group')===sg;});}" +
                "var smax=+(sw.getAttribute('data-ff-max-selectable')||'0');if(smax>0&&ss.keys.length>=smax){el.checked=false;syncSel(sw);return;}" +
                "ss.keys.push(el.value);}" +
                "else if(!el.checked&&si>=0){ss.keys.splice(si,1);}" +
                "writeSel(sw,ss);syncSel(sw);return;}" +
                "if(el.matches('[data-ff-hours],[data-ff-minutes],[data-ff-seconds]')){var pk=el.closest('[data-ff-picker]');" +
                "if(pk){var dayIso=null;var st=pk.getAttribute('data-ff-state')||'';var d=parseYmd(st);if(d)dayIso=ymd(d);" +
                "setPickerValue(pk,composeValue(pk,dayIso));}return;}" +
                "if(el.matches('[data-ff-column-toggle]')){var wrap=el.closest('.ff-table-wrap');if(!wrap)return;" +
                "var shown=[];wrap.querySelectorAll('[data-ff-column-toggle]').forEach(function(box){if(box.checked)shown.push(box.getAttribute('data-ff-column-toggle')||'');});" +
                "var url=new URL(location.href);url.searchParams.set('columns',shown.filter(Boolean).join(','));url.searchParams.set('page','1');" +
                "ffTableNav(url.pathname+url.search,wrap);return;}" +
                "if(el.matches('[data-ff-mobile-sort]')){var wrap=el.closest('.ff-table-wrap');if(!wrap)return;" +
                "var parts=(el.value||'').split(':');var url=new URL(location.href);" +
                "if(parts[0]){url.searchParams.set('sort',parts[0]);url.searchParams.set('direction',parts[1]||'asc');}" +
                "else{url.searchParams.delete('sort');url.searchParams.delete('direction');}" +
                "url.searchParams.set('page','1');ffTableNav(url.pathname+url.search,wrap);return;}" +
                "if(!el.matches('[data-ff-table-nav-select]'))return;" +
                "var wrap=el.closest('.ff-table-wrap');var form=el.closest('form[data-ff-table-nav]');" +
                "if(form){if(form.requestSubmit)form.requestSubmit();else form.submit();return;}" +
                "var url=new URL(location.href);url.searchParams.set(el.name||'perPage',el.value);url.searchParams.set('page','1');" +
                "ffTableNav(url.pathname+url.search,wrap);});" +
                "document.addEventListener('mouseover',function(ev){var db=ev.target&&ev.target.closest?ev.target.closest('button[data-ff-day]'):null;if(!db)return;" +
                "var rp=db.closest('[data-ff-picker]');if(!rp||!rp.getAttribute('data-ff-range-pending'))return;var hd=db.getAttribute('data-ff-day');" +
                "if(rp.getAttribute('data-ff-range-hover')===hd)return;rp.setAttribute('data-ff-range-hover',hd);" +
                "rebuildView(rp,parseYmd(rp.getAttribute('data-ff-focused'))||parseYmd(hd));});" +
                "document.addEventListener('keydown',function(ev){" +
                "if(ev.key==='Enter'&&ev.target&&ev.target.matches&&ev.target.matches('[data-ff-record-url],[data-ff-record-action]')){" +
                "ev.preventDefault();ev.target.click();return;}" +
                "if(ev.key==='Escape'){" +
                "root.classList.remove('ff-sidebar-open');closeMenus();closeDialogs();closePickers();closeComboboxes();}});" +
                "function repositionOpenRowMenus(){root.querySelectorAll('.ff-row-action-menu.ff-open').forEach(positionRowMenu);}" +
                "window.addEventListener('scroll',repositionOpenRowMenus,true);" +
                "window.addEventListener('resize',repositionOpenRowMenus);" +
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
