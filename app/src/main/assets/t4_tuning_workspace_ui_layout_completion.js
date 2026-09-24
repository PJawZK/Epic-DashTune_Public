/* TUNER_UI_LAYOUT_COMPLETION_V1 compatibility / TUNER_UI_LAYOUT_COMPLETION_V2 / TUNER_UI_GEOMETRY_AUTHORITY_1198
 * Legacy camera marker: table3dPitch=142;table3dYaw=38
 * Presentation-only geometry authority for the locked Tuner UI.
 * The stylesheet is appended in a microtask so it wins after the other presentation
 * layers without adding another asset or changing native/tuning authority.
 */
  const t4UiLayoutStyle=document.createElement('style');
  t4UiLayoutStyle.id='t4-tuning-ui-layout-completion-style';
  t4UiLayoutStyle.textContent=`
    #page-tuning,#page-tuning *{box-sizing:border-box}
    html,body,body.t4tw-tuner-active,body.t4tw-tuner-active>#app,
    body.t4tw-tuner-active main,#page-tuning{margin-top:0!important;padding-top:0!important}
    /* TUNER_UI_CORRECTIONS_1197_TOP_EDGE */
    body.t4tw-tuner-active>#app{inset-block-start:0!important;grid-template-rows:minmax(0,1fr)!important;grid-template-columns:minmax(0,1fr)!important;gap:0!important;padding:0!important}
    body.t4tw-tuner-active>#app>main{grid-row:1!important;grid-column:1!important;height:100%!important;min-height:0!important;padding:0!important}
    #page-tuning{top:0!important;--t4-space-tight:4px;--t4-space:8px;--t4-space-section:12px;--t4-binary-height:24px;--t4-binary-width:72px}
    #t4twTopProfile{display:none!important}
    .t4tw-appstatus{min-width:0}
    .t4tw-condition{display:none!important}
    .t4tw-settings-field.t4tw-condition-disabled,.t4tw-settings-panel.t4tw-condition-disabled,
    .t4tw-hierarchy-node.t4tw-condition-disabled,.t4tw-curve-row.t4tw-condition-disabled{opacity:.48!important;filter:saturate(.35);cursor:not-allowed!important}
    .t4tw-inactive-badge{display:inline-flex;margin-left:7px;padding:2px 6px;border:1px solid #42515a;border-radius:999px;color:#8fa0aa;font-size:8px;font-weight:800;vertical-align:middle}
    .t4tw-shell{gap:0!important}
    .t4tw-nav-row{gap:var(--t4-space)!important;margin-bottom:var(--t4-space)!important}
    .t4tw-parameters-main,.t4tw-table-main,.t4tw-curve-main{gap:var(--t4-space)!important}
    .t4tw-parameters-shell,.t4tw-table-shell,.t4tw-curve-shell{gap:var(--t4-space)!important}
    .t4tw-table-head,.t4tw-curve-head{gap:var(--t4-space)!important;min-height:40px!important;align-items:center}
    .t4tw-table-editbar,.t4tw-curve-editbar{gap:var(--t4-space-tight)!important;padding:var(--t4-space)!important;border:1px solid #213e4c!important;border-radius:9px!important;background:#07131a!important;align-items:center!important}
    .t4tw-table-extra-tools{gap:var(--t4-space-tight)!important}
    .t4tw-table-viewport{padding:4px!important}
    .t4tw-inline-parameters{gap:var(--t4-space)!important;padding:0 1px 4px!important}
    .t4tw-inline-parameters>.t4tw-settings-root{display:grid!important;grid-template-columns:minmax(0,1fr)!important;gap:var(--t4-space)!important;border:0!important;background:transparent!important;padding:0!important}
    .t4tw-settings-root>.t4tw-settings-panel-wrap{display:block!important;min-width:0!important;width:100%!important}
    .t4tw-inline-parameters .t4tw-settings-panel:not(.t4tw-settings-root){width:100%!important;min-width:0!important;display:grid!important;column-gap:var(--t4-space)!important;row-gap:0!important;overflow:hidden!important;border-color:#244552!important;background:linear-gradient(180deg,#0b1c25,#07131a)!important;padding:0 10px 8px!important}
    .t4tw-inline-parameters .t4tw-settings-panel:not(.t4tw-settings-root)>.t4tw-settings-panel-title{grid-column:1/-1!important;margin:0 -10px 0!important;padding:9px 10px!important;border-bottom:1px solid #1d3946!important;background:#0c222d!important;color:#21c8f6!important;font-size:13px!important;text-transform:none!important;letter-spacing:0!important}
    .t4tw-settings-panel-title{display:flex;align-items:center;gap:7px}
    .t4tw-inline-parameters .t4tw-settings-text,.t4tw-inline-parameters .t4tw-settings-command,.t4tw-inline-parameters .t4tw-settings-panel:not(.t4tw-settings-root)>.t4tw-settings-panel{grid-column:1/-1!important}
    .t4tw-inline-parameters .t4tw-settings-field,.t4tw-settings-root>.t4tw-settings-field{min-width:0!important;min-height:44px!important;margin:0!important;padding:7px 3px!important;border:0!important;border-bottom:1px solid #18313d!important;border-radius:0!important;background:transparent!important;grid-template-columns:minmax(0,1fr) minmax(110px,.55fr)!important;gap:var(--t4-space)!important;align-items:center!important}
    .t4tw-inline-parameters .t4tw-settings-field-label{min-width:0;white-space:normal;overflow-wrap:anywhere;line-height:1.2}
    .t4tw-inline-parameters .t4tw-settings-field-value{min-width:0!important;max-width:100%!important;justify-self:stretch;text-align:right}
    .t4tw-inline-parameters .t4tw-param-input-wrap{grid-template-columns:minmax(76px,1fr) auto!important;gap:7px!important;min-width:0!important}
    .t4tw-inline-parameters .t4tw-bit-control{width:100%!important;min-width:0!important;max-width:100%!important}
    /* TUNER_UI_1198_BINARY_SELECTOR_3_TO_1 */
    .t4tw-inline-parameters .t4tw-settings-field.t4tw-boolean-field{grid-template-columns:minmax(0,1fr) var(--t4-binary-width)!important}
    .t4tw-inline-parameters .t4tw-settings-field.t4tw-boolean-field .t4tw-settings-field-value,.t4tw-inline-parameters .t4tw-settings-field.t4tw-boolean-field .t4tw-bit-control{width:var(--t4-binary-width)!important;min-width:var(--t4-binary-width)!important;max-width:var(--t4-binary-width)!important;justify-self:end!important;padding:0!important;border:0!important;background:transparent!important}
    .t4tw-bit-toggle.t4tw-boolean-toggle{width:var(--t4-binary-width)!important;min-width:var(--t4-binary-width)!important;max-width:var(--t4-binary-width)!important;height:var(--t4-binary-height)!important;padding:0!important;border-radius:12px!important;color:transparent!important;font-size:0!important;overflow:hidden!important;position:relative!important}
    .t4tw-bit-toggle.t4tw-boolean-toggle:before{width:18px!important;height:18px!important;left:3px!important;top:2px!important}
    .t4tw-bit-toggle.t4tw-boolean-toggle.on:before{left:calc(100% - 21px)!important}
    .t4tw-bit-toggle.t4tw-boolean-toggle.unknown{background:#111d24!important;border-color:#52636d!important;opacity:.78}
    .t4tw-bit-toggle.t4tw-boolean-toggle.unknown:before{left:calc(50% - 9px)!important;background:#84949d!important}
    .t4tw-curve-manager-head{padding:9px 10px!important}
    .t4tw-curve-list{gap:0!important;padding:0!important}
    .t4tw-curve-row{border-width:0 0 1px 3px!important;border-radius:0!important;padding:8px 10px!important;background:transparent!important}
    .t4tw-curve-row:last-child{border-bottom-width:0!important}
    .t4tw-curve-add{padding:8px 10px!important}
    .t4tw-curve-editbar .t4tw-table-selection{min-width:0!important}
    .t4tw-telemetry{display:flex!important;grid-template-columns:none!important;flex-wrap:nowrap!important;overflow:hidden!important;gap:var(--t4-space-tight)!important;padding:6px 0!important;min-height:0!important;border-top:1px solid #18313d!important}
    .t4tw-telemetry-slot{min-width:0!important;flex:1 1 0!important;padding:6px 8px!important}
    body[data-t4tw-viewport="tablet-portrait"] .t4tw-grid,body[data-t4tw-viewport="phone-portrait"] .t4tw-grid{grid-template-columns:1fr!important}
    body[data-t4tw-viewport="tablet-landscape"] .t4tw-shell{padding:8px 12px 0!important}
    body[data-t4tw-viewport="tablet-landscape"] .t4tw-parameters-shell,body[data-t4tw-viewport="tablet-landscape"] .t4tw-table-shell,body[data-t4tw-viewport="tablet-landscape"] .t4tw-curve-shell{grid-template-columns:180px minmax(0,1fr)!important}
    body[data-t4tw-viewport="tablet-landscape"] .t4tw-system-rail{display:flex!important;flex-direction:column}
    body[data-t4tw-viewport="tablet-landscape"] .t4tw-inline-parameters .t4tw-settings-panel:not(.t4tw-settings-root){grid-template-columns:repeat(2,minmax(0,1fr))!important}
    body[data-t4tw-viewport="tablet-portrait"] .t4tw-appbar{min-height:0!important;grid-template-columns:auto minmax(0,1fr)!important;grid-template-rows:auto auto!important;gap:5px 9px!important;padding:5px 10px!important}
    body[data-t4tw-viewport="tablet-portrait"] .t4tw-brand{grid-column:1;grid-row:1;font-size:21px}
    body[data-t4tw-viewport="tablet-portrait"] .t4tw-appstatus{grid-column:2;grid-row:1;justify-content:flex-end;gap:5px}
    body[data-t4tw-viewport="tablet-portrait"] .t4tw-appnav{grid-column:1/-1;grid-row:2;max-width:none!important;grid-template-columns:repeat(4,minmax(0,1fr));gap:4px}
    body[data-t4tw-viewport="tablet-portrait"] .t4tw-appnav-btn{padding:6px 7px;font-size:11px}
    body[data-t4tw-viewport="tablet-portrait"] .t4tw-shell{padding:6px 8px 0!important}
    body[data-t4tw-viewport="tablet-portrait"] .t4tw-nav-row{grid-template-columns:1fr!important;margin-bottom:var(--t4-space-tight)!important}
    body[data-t4tw-viewport="tablet-portrait"] .t4tw-content-modes{width:100%}
    body[data-t4tw-viewport="tablet-portrait"] .t4tw-content-mode{min-height:37px}
    body[data-t4tw-viewport="tablet-portrait"] .t4tw-system-rail{display:none!important}
    body[data-t4tw-viewport="tablet-portrait"] .t4tw-parameters-shell,body[data-t4tw-viewport="tablet-portrait"] .t4tw-table-shell,body[data-t4tw-viewport="tablet-portrait"] .t4tw-curve-shell{grid-template-columns:minmax(0,1fr)!important}
    body[data-t4tw-viewport="tablet-portrait"] .t4tw-inline-parameters .t4tw-settings-panel:not(.t4tw-settings-root){grid-template-columns:1fr!important}
    body[data-t4tw-viewport="tablet-portrait"] .t4tw-table-head,body[data-t4tw-viewport="tablet-portrait"] .t4tw-curve-head{flex-wrap:wrap!important;align-items:flex-start!important}
    body[data-t4tw-viewport="tablet-portrait"] .t4tw-table-view-controls,body[data-t4tw-viewport="tablet-portrait"] .t4tw-curve-mode{width:100%!important;margin-left:0!important;justify-content:flex-end!important}
    body[data-t4tw-viewport="tablet-portrait"] .t4tw-table-viewport{height:clamp(360px,52vh,760px)!important}

    /* TUNER_UI_1198_PHONE_RESPONSIVE_GEOMETRY */
    body[data-t4tw-viewport="phone-portrait"] .t4tw-appbar{min-height:0!important;grid-template-columns:auto minmax(0,1fr)!important;grid-template-rows:auto auto!important;gap:3px 5px!important;padding:4px 5px!important}
    body[data-t4tw-viewport="phone-portrait"] .t4tw-brand{grid-column:1;grid-row:1;font-size:18px!important}
    body[data-t4tw-viewport="phone-portrait"] .t4tw-appstatus{grid-column:2;grid-row:1;justify-content:flex-end!important;overflow:visible!important;gap:4px!important}
    body[data-t4tw-viewport="phone-portrait"] .t4tw-top-action{height:34px!important;padding:0 7px!important;font-size:10px!important}
    body[data-t4tw-viewport="phone-portrait"] .t4tw-appnav{grid-column:1/-1;grid-row:2!important;gap:2px!important}
    body[data-t4tw-viewport="phone-portrait"] .t4tw-appnav-btn{min-height:34px!important;padding:5px 6px!important;font-size:10px!important;gap:4px!important}
    body[data-t4tw-viewport="phone-portrait"] .t4tw-shell{padding:4px 5px 0!important}
    body[data-t4tw-viewport="phone-portrait"] .t4tw-nav-row{grid-template-columns:1fr!important;gap:4px!important;margin-bottom:4px!important}
    body[data-t4tw-viewport="phone-portrait"] .t4tw-hierarchy-selectors{grid-template-columns:1fr!important;gap:4px!important}
    body[data-t4tw-viewport="phone-portrait"] .t4tw-hierarchy-chevron{display:none!important}
    body[data-t4tw-viewport="phone-portrait"] .t4tw-hierarchy-select{min-height:38px!important;padding:7px 30px 7px 10px!important;font-size:11px!important;border-radius:8px!important}
    body[data-t4tw-viewport="phone-portrait"] .t4tw-content-modes{display:grid!important;grid-template-columns:repeat(3,1fr)!important}
    body[data-t4tw-viewport="phone-portrait"] .t4tw-content-mode{min-height:36px!important;font-size:10px!important;gap:5px!important}
    body[data-t4tw-viewport="phone-portrait"] .t4tw-content-mode svg{width:16px!important;height:16px!important}
    body[data-t4tw-viewport="phone-portrait"] .t4tw-system-rail{display:none!important}
    body[data-t4tw-viewport="phone-portrait"] .t4tw-parameters-shell,body[data-t4tw-viewport="phone-portrait"] .t4tw-table-shell,body[data-t4tw-viewport="phone-portrait"] .t4tw-curve-shell{grid-template-columns:minmax(0,1fr)!important}
    body[data-t4tw-viewport="phone-portrait"] .t4tw-inline-parameters .t4tw-settings-panel:not(.t4tw-settings-root){grid-template-columns:1fr!important}
    body[data-t4tw-viewport="phone-portrait"] .t4tw-inline-parameters .t4tw-settings-field{min-height:40px!important;padding:5px 2px!important;gap:5px!important}
    body[data-t4tw-viewport="phone-portrait"] .t4tw-inactive-badge{margin-left:5px!important;padding:1px 5px!important;font-size:7px!important}
    body[data-t4tw-viewport="phone-portrait"] .t4tw-table-head,body[data-t4tw-viewport="phone-portrait"] .t4tw-curve-head{flex-wrap:wrap!important;align-items:flex-start!important;gap:5px!important}
    body[data-t4tw-viewport="phone-portrait"] .t4tw-table-view-controls,body[data-t4tw-viewport="phone-portrait"] .t4tw-curve-mode{width:100%!important;margin-left:0!important;justify-content:flex-start!important;gap:4px!important}
    body[data-t4tw-viewport="phone-portrait"] .t4tw-table-view-controls .t4tw-btn,body[data-t4tw-viewport="phone-portrait"] .t4tw-curve-mode .t4tw-btn{height:36px!important;min-width:42px!important;padding:0 9px!important;border-radius:8px!important}
    body[data-t4tw-viewport="phone-portrait"] #t4twInlineTableZoom{min-width:40px!important;width:auto!important;text-align:center!important}
    body[data-t4tw-viewport="phone-portrait"] .t4tw-table-viewport{height:clamp(300px,49vh,620px)!important;padding:4px 8px 4px 4px!important;overflow:auto!important}
    body[data-t4tw-viewport="phone-portrait"] .t4tw-table-editbar,body[data-t4tw-viewport="phone-portrait"] .t4tw-curve-editbar{overflow:visible!important;flex-wrap:wrap!important;padding:6px!important;gap:4px!important}
    body[data-t4tw-viewport="phone-portrait"] #t4twInlineTableSelection{flex:1 0 100%!important;min-width:0!important}
    body[data-t4tw-viewport="phone-portrait"] .t4tw-table-editbar>.t4tw-btn,body[data-t4tw-viewport="phone-portrait"] .t4tw-curve-editbar>.t4tw-btn{height:38px!important;min-width:46px!important;padding:0 10px!important}
    body[data-t4tw-viewport="phone-portrait"] .t4tw-table-value-input,body[data-t4tw-viewport="phone-portrait"] .t4tw-curve-value-input{height:38px!important;width:82px!important;flex:0 0 82px!important;padding:6px 8px!important}
    body[data-t4tw-viewport="phone-portrait"] .t4tw-table-extra-tools{flex:1 0 100%!important;display:grid!important;grid-template-columns:repeat(3,minmax(0,1fr))!important;gap:4px!important}
    body[data-t4tw-viewport="phone-portrait"] .t4tw-table-extra-tools .t4tw-btn{width:100%!important;min-width:0!important;height:34px!important;padding:0 4px!important;font-size:9px!important}
    body[data-t4tw-viewport="phone-portrait"] .t4tw-curve-workspace{flex:0 0 auto!important;grid-template-columns:minmax(0,1fr)!important;grid-template-rows:minmax(250px,40vh) auto!important;gap:5px!important}
    body[data-t4tw-viewport="phone-portrait"] .t4tw-curve-plot-wrap,body[data-t4tw-viewport="phone-portrait"] .t4tw-curve-svg{min-height:250px!important;height:40vh!important;max-height:420px!important}
    body[data-t4tw-viewport="phone-portrait"] .t4tw-curve-manager{max-height:132px!important;min-height:72px!important}
    body[data-t4tw-viewport="phone-portrait"] .t4tw-curve-manager-head{padding:6px 8px!important;font-size:10px!important}
    body[data-t4tw-viewport="phone-portrait"] .t4tw-curve-list{display:flex!important;flex-direction:row!important;overflow-x:auto!important;overflow-y:hidden!important}
    body[data-t4tw-viewport="phone-portrait"] .t4tw-curve-row{min-width:178px!important;max-width:220px!important;flex:0 0 auto!important;padding:6px 7px!important;border-right:1px solid #18313d!important;border-bottom:0!important}
    body[data-t4tw-viewport="phone-portrait"] .t4tw-curve-add{flex:0 0 auto!important;padding:6px 7px!important}
    body[data-t4tw-viewport="phone-portrait"] .t4tw-curve-editbar>.t4tw-table-selection:not(#t4twInlineCurveSelection):not(#t4twInlineCurveX){flex:1 0 100%!important;white-space:nowrap!important;overflow:hidden!important;text-overflow:ellipsis!important}
    body[data-t4tw-viewport="phone-portrait"] #t4twInlineCurveSelection,body[data-t4tw-viewport="phone-portrait"] #t4twInlineCurveX{min-width:0!important;flex:0 0 auto!important}
    body[data-t4tw-viewport="phone-portrait"] .t4tw-telemetry{overflow-x:auto!important;overflow-y:hidden!important;padding:4px 0!important;gap:4px!important}
    body[data-t4tw-viewport="phone-portrait"] .t4tw-telemetry-slot{flex:0 0 92px!important;padding:5px 7px!important}

    body[data-t4tw-viewport="phone-landscape"] .t4tw-appbar{padding:4px 8px!important;gap:5px!important}
    body[data-t4tw-viewport="phone-landscape"] .t4tw-brand{font-size:19px!important}
    body[data-t4tw-viewport="phone-landscape"] .t4tw-appnav-btn{min-height:34px!important;padding:5px 7px!important;font-size:10px!important;gap:5px!important}
    body[data-t4tw-viewport="phone-landscape"] .t4tw-top-action{height:34px!important;padding:0 8px!important;font-size:10px!important}
    body[data-t4tw-viewport="phone-landscape"] .t4tw-shell{padding:4px 8px 0!important}
    body[data-t4tw-viewport="phone-landscape"] .t4tw-nav-row{gap:6px!important;margin-bottom:5px!important}
    body[data-t4tw-viewport="phone-landscape"] .t4tw-hierarchy-selectors{gap:4px!important}
    body[data-t4tw-viewport="phone-landscape"] .t4tw-hierarchy-select{min-height:36px!important;padding:6px 26px 6px 9px!important;font-size:10px!important}
    body[data-t4tw-viewport="phone-landscape"] .t4tw-content-mode{min-height:36px!important;font-size:10px!important;gap:5px!important}
    body[data-t4tw-viewport="phone-landscape"] .t4tw-system-rail{display:none!important}
    body[data-t4tw-viewport="phone-landscape"] .t4tw-parameters-shell,body[data-t4tw-viewport="phone-landscape"] .t4tw-table-shell,body[data-t4tw-viewport="phone-landscape"] .t4tw-curve-shell{grid-template-columns:minmax(0,1fr)!important}
    body[data-t4tw-viewport="phone-landscape"] .t4tw-inline-parameters .t4tw-settings-panel:not(.t4tw-settings-root){grid-template-columns:repeat(2,minmax(0,1fr))!important}
    body[data-t4tw-viewport="phone-landscape"] .t4tw-table-head,body[data-t4tw-viewport="phone-landscape"] .t4tw-curve-head{gap:6px!important;min-height:34px!important}
    body[data-t4tw-viewport="phone-landscape"] .t4tw-table-view-controls,body[data-t4tw-viewport="phone-landscape"] .t4tw-curve-mode{gap:4px!important}
    body[data-t4tw-viewport="phone-landscape"] .t4tw-table-view-controls .t4tw-btn,body[data-t4tw-viewport="phone-landscape"] .t4tw-curve-mode .t4tw-btn{height:34px!important;min-width:40px!important;padding:0 8px!important}
    body[data-t4tw-viewport="phone-landscape"] .t4tw-table-editbar,body[data-t4tw-viewport="phone-landscape"] .t4tw-curve-editbar{flex-wrap:wrap!important;overflow:visible!important;padding:6px!important;gap:4px!important}
    body[data-t4tw-viewport="phone-landscape"] .t4tw-table-extra-tools{flex-wrap:wrap!important}
    body[data-t4tw-viewport="phone-landscape"] .t4tw-curve-workspace{grid-template-columns:minmax(0,1fr)!important;grid-template-rows:minmax(180px,1fr) auto!important;gap:5px!important}
    body[data-t4tw-viewport="phone-landscape"] .t4tw-curve-plot-wrap,body[data-t4tw-viewport="phone-landscape"] .t4tw-curve-svg{min-height:180px!important}
    body[data-t4tw-viewport="phone-landscape"] .t4tw-curve-manager{max-height:108px!important;min-height:64px!important}
    body[data-t4tw-viewport="phone-landscape"] .t4tw-curve-manager-head{padding:5px 8px!important;font-size:10px!important}
    body[data-t4tw-viewport="phone-landscape"] .t4tw-curve-list{display:flex!important;flex-direction:row!important;overflow-x:auto!important;overflow-y:hidden!important}
    body[data-t4tw-viewport="phone-landscape"] .t4tw-curve-row{min-width:180px!important;max-width:230px!important;flex:0 0 auto!important;padding:5px 7px!important;border-right:1px solid #18313d!important;border-bottom:0!important}
    body[data-t4tw-viewport="phone-landscape"] .t4tw-curve-add{flex:0 0 auto!important;padding:5px 7px!important}
    body[data-t4tw-viewport="phone-landscape"] .t4tw-telemetry{overflow-x:auto!important;overflow-y:hidden!important;padding:4px 0!important}
    body[data-t4tw-viewport="phone-landscape"] .t4tw-telemetry-slot{flex:0 0 132px!important;padding:5px 7px!important}

    .t4tw-help-button{margin-left:auto;width:24px;height:24px;border-radius:50%;border:1.5px solid #5f9bb9;background:#091821;color:#8cdcff;font-weight:950;font-size:14px;line-height:20px;padding:0;text-align:center;cursor:pointer;flex:0 0 auto}
    .t4tw-help-dialog{width:min(620px,94vw);max-height:min(72vh,680px);display:flex;flex-direction:column}
    .t4tw-help-copy{overflow:auto;overscroll-behavior:contain;padding:4px 2px 2px;color:#c4d5de;font-size:13px;line-height:1.48;white-space:pre-wrap}
    .t4tw-help-copy p{margin:0 0 12px}
    .t4tw-curve-svg [data-curve-point]{pointer-events:all}
    .t4tw-curve-point-touch{stroke-width:5!important}
  `;
  queueMicrotask(()=>document.head.appendChild(t4UiLayoutStyle));
  topProfileButton?.remove();
  conditionVisible=function(){return true;};
  makeConditionNode=function(){return null;};
  if(workspace?.status==='ready')rebuildWorkspaceIndex();
  const t4UiBaseSettingsFieldNode=settingsFieldNode;
  settingsFieldNode=function(entry,inheritedEnabled=true){const node=t4UiBaseSettingsFieldNode(entry,inheritedEnabled);if(node&&!(inheritedEnabled&&conditionUsable(entry))){node.classList.add('t4tw-condition-disabled');node.setAttribute('aria-disabled','true');const label=node.querySelector('.t4tw-settings-field-label');if(label&&!label.querySelector('.t4tw-inactive-badge')){const badge=document.createElement('span');badge.className='t4tw-inactive-badge';badge.textContent='Not active';label.appendChild(badge);}}return node;};
  const t4UiHelp=document.createElement('div');t4UiHelp.className='t4tw-modal';t4UiHelp.id='t4twParameterHelpModal';t4UiHelp.hidden=true;t4UiHelp.innerHTML='<div class="t4tw-dialog t4tw-help-dialog" role="dialog" aria-modal="true"><div class="t4tw-dialog-head"><div class="t4tw-dialog-title" id="t4twParameterHelpTitle">Parameter information</div><button class="t4tw-close" type="button">Close</button></div><div class="t4tw-help-copy" id="t4twParameterHelpCopy"></div></div>';page.appendChild(t4UiHelp);t4UiHelp.querySelector('.t4tw-close').onclick=()=>t4UiHelp.hidden=true;t4UiHelp.addEventListener('click',e=>{if(e.target===t4UiHelp)t4UiHelp.hidden=true;});
  function t4UiOpenHelp(title,paragraphs){t4UiHelp.querySelector('#t4twParameterHelpTitle').textContent=title||'Parameter information';const copy=t4UiHelp.querySelector('#t4twParameterHelpCopy');copy.textContent='';paragraphs.forEach(text=>{const p=document.createElement('p');p.textContent=text;copy.appendChild(p);});t4UiHelp.hidden=false;}
  function t4UiCollapseHelp(root){for(const panel of root.querySelectorAll?.('.t4tw-settings-panel')||[]){if(panel.dataset.t4twHelpCollapsed==='1')continue;const texts=Array.from(panel.children).filter(n=>n.classList?.contains('t4tw-settings-text')&&!/^No scalar or enum parameters/i.test(String(n.textContent||'').trim()));if(!texts.length){panel.dataset.t4twHelpCollapsed='1';continue;}const paragraphs=texts.map(n=>String(n.textContent||'').trim()).filter(Boolean);texts.forEach(n=>n.remove());let title=panel.querySelector(':scope > .t4tw-settings-panel-title');if(!title){title=document.createElement('div');title.className='t4tw-settings-panel-title';title.textContent='Information';panel.prepend(title);}const button=document.createElement('button');button.type='button';button.className='t4tw-help-button';button.textContent='?';button.setAttribute('aria-label','Show parameter information');button.onclick=e=>{e.stopPropagation();t4UiOpenHelp(String(title.textContent||'Parameter information').replace(/\?$/,'').trim(),paragraphs);};title.appendChild(button);panel.dataset.t4twHelpCollapsed='1';}}
  const t4UiBaseRenderSettingsDialogInto=renderSettingsDialogInto;renderSettingsDialogInto=function(container,dialogId,stack=[],depth=0,inheritedEnabled=true){const result=t4UiBaseRenderSettingsDialogInto(container,dialogId,stack,depth,inheritedEnabled);t4UiCollapseHelp(container);return result;};
  function t4UiPatchCurveTouch(){page.querySelectorAll('.t4tw-curve-svg [data-curve-point]').forEach(point=>{if(point.dataset.t4twImmediateTouch==='1')return;point.dataset.t4twImmediateTouch='1';point.classList.add('t4tw-curve-point-touch');if(Number(point.getAttribute('r')||0)<9)point.setAttribute('r','9');point.addEventListener('pointerdown',e=>{if(e.pointerType!=='touch'&&e.pointerType!=='pen')return;const index=Number(point.getAttribute('data-curve-point'));if(!Number.isFinite(index))return;e.preventDefault();e.stopPropagation();selectCurvePoint(index);},{passive:false});});}
  const t4UiBaseRenderCurveGraph=renderCurveGraph;renderCurveGraph=function(){const result=t4UiBaseRenderCurveGraph();t4UiPatchCurveTouch();return result;};
  table3dYaw=38;table3dPitch=142;
  table3dReset=function(view='iso'){table3dZoom=1;table3dPanX=0;table3dPanY=0;if(view==='top'){table3dYaw=0;table3dPitch=8;}else if(view==='front'){table3dYaw=0;table3dPitch=90;}else if(view==='side'){table3dYaw=90;table3dPitch=90;}else{table3dYaw=38;table3dPitch=142;}renderInlineTable3d();};
  window.addEventListener('epicdash:page-activated',e=>{if(e?.detail?.pageId!=='page-tuning')return;setTimeout(()=>{if(workspace?.status==='ready')rebuildWorkspaceIndex();t4UiCollapseHelp(page);},40);});