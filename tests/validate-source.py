#!/usr/bin/env python3
"""Checks each target's source/class/resource identity without network or Minecraft jars."""
from pathlib import Path
import json,re,sys
root=Path(__file__).resolve().parents[1]
common=root/'shared/common/java'
common_res=root/'shared/common/resources'
shared_mc=root/'shared/mc-1.21.1/client/java'
errors=[]
for target in ('forge-1.20.1','neoforge-1.21.1','fabric-1.21.1'):
    home=root/'targets'/target
    java_dirs=[home/'src/main/java',home/'src/client/java',common]
    if target!='forge-1.20.1':java_dirs.append(shared_mc)
    classes={}
    for path in [p for d in java_dirs if d.exists() for p in d.rglob('*.java')]:
        source=path.read_text(encoding='utf-8')
        pkg=re.search(r'^package\s+([\w.]+);',source,re.M)
        if not pkg:errors.append(f'{target}: no package in {path}');continue
        class_name=pkg.group(1)+'.'+path.stem
        if class_name in classes:errors.append(f'{target}: duplicate {class_name} at {path} and {classes[class_name]}')
        classes[class_name]=path
    needed=('com.macat.waystonemap.WaystoneGeometry','com.macat.waystonemap.WaystoneReflection',
            'com.macat.waystonemap.XaeroCommandRewriter','com.macat.waystonemap.XaeroScopedCommandRewrite','com.macat.waystonemap.XaeroWaypointDisplayDefault', 'com.macat.waystonemap.XaeroLegacyConfigRepair',
            'com.macat.waystonemap.XaeroWorldSafetyBridge','com.macat.waystonemap.WaystoneMapTpMod')
    for k in needed:
        if k not in classes:errors.append(f'{target}: missing {k}')
    for resource in ['logo.png','pack.mcmeta']:
        if not (common_res/resource).exists():errors.append(f'{target}: missing {resource}')
    mixin_file=home/'src/main/resources'/('fast_travel_waypoints.client.mixins.json' if target=='fabric-1.21.1' else 'fast_travel_waypoints.mixins.json')
    config=json.loads(mixin_file.read_text())
    if target != 'forge-1.20.1':
        if 'XaeroClientCommandSendMixin' not in config.get('client',[]):
            errors.append(f'{target}: missing scoped Xaero command sender mixin')
        for old in ('XaeroWaypointTeleportCommandMixin','XaeroMapTeleporterCommandMixin','XaeroPlayerTeleporterCommandMixin'):
            if old in config.get('client',[]):
                errors.append(f'{target}: legacy third-party command mixin still enabled: {old}')
    for clazz in config.get('mixins',[])+config.get('client',[]):
        fqn=config['package']+'.'+clazz
        if fqn not in classes:errors.append(f'{target}: missing mixin {fqn}')
    entrypoint=classes.get('com.macat.waystonemap.WaystoneMapTpMod')
    if entrypoint:
        s=entrypoint.read_text()
        if 'new WaystoneGeometry(' not in s or 'new WaystoneReflection(' not in s:
            errors.append(f'{target}: main class not wired to common Waystones helpers')
        for fn in ['GEOMETRY.findWaystoneBottom','GEOMETRY.isWaystoneBlock','GEOMETRY.findSafeTeleportPos',
                   'WAYSTONE_REFLECTION.getWaystoneObject','WAYSTONE_REFLECTION.chooseDisplayName']:
            if fn not in s: errors.append(f'{target}: no reference to {fn}')
    print(f'{target}: {len(classes)} unique Java classes, {len(config.get("client",[]))} client mixins, shared helpers found')
if errors:
    for e in errors:print('ERROR:',e)
    sys.exit(1)
print('Source matrix checks passed (not a substitute for Gradle compilation/runtime tests).')
