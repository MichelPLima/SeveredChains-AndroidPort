"""Gera android/app/src/main/assets/licencas/bibliotecas-java.txt: as bibliotecas Java que vão no APK, com licença, projeto e
os textos de LICENSE/NOTICE que vêm dentro de cada jar (MIT/BSD pedem o aviso de copyright; Apache pede os NOTICE).

Fonte: a lista de jars do APK (assets/java/classpath.txt de um APK gerado) e os POMs no cache do Gradle (a mesma resolução do
build do Severed Chains). Rodar de novo sempre que as dependências mudarem; o build do APK confere se a lista está completa.

Uso (na raiz do repositório): python -I scripts/gerar-licencas-java.py <apk>
"""
import datetime
import hashlib
import os
import re
import sys
import urllib.request
import xml.etree.ElementTree as ET
import zipfile

RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SAIDA = os.path.join(RAIZ, 'android', 'app', 'src', 'main', 'assets', 'licencas', 'bibliotecas-java.txt')
LIBS = [os.path.join(RAIZ, 'upstream', 'build', 'libs', 'libs'), os.path.join(RAIZ, 'upstream', 'build', 'libs')]
CACHE = os.path.join(os.path.expanduser('~'), '.gradle', 'caches', 'modules-2', 'files-2.1')
NOSSOS = {'lod-game-snapshot.jar', 'runtime-test.jar'}

# Projetos MIT/BSD cujo jar não traz o texto da licença (com o aviso de copyright): o texto do repositório oficial, na versão usada
TEXTO_DO_REPOSITORIO = {
    'antlr4': 'https://raw.githubusercontent.com/antlr/antlr4/4.13.2/LICENSE.txt',
    'antlr4-runtime': 'https://raw.githubusercontent.com/antlr/antlr4/4.13.2/LICENSE.txt',
    'antlr-runtime': 'https://raw.githubusercontent.com/antlr/antlr3/3.5.3/tool/LICENSE.txt',
    'ST4': 'https://raw.githubusercontent.com/antlr/stringtemplate4/ST4-4.3.4/LICENSE.txt',
    'discord-game-sdk4j': 'https://raw.githubusercontent.com/JnCrMx/discord-game-sdk4j/v1.0.0/LICENSE',
    'joml': 'https://raw.githubusercontent.com/JOML-CI/JOML/1.10.8/LICENSE',
    'semver4j': 'https://raw.githubusercontent.com/vdurmont/semver4j/v3.1.0/LICENSE.md',
    'org.abego.treelayout.core': 'https://raw.githubusercontent.com/abego/treelayout/v1.0.3/org.abego.treelayout/src/LICENSE.TXT',
}
_textos_baixados = {}


def texto_do_repositorio(url):
    if url not in _textos_baixados:
        pedido = urllib.request.Request(url, headers={'User-Agent': 'licencas-severedchains-android'})
        with urllib.request.urlopen(pedido, timeout=30) as r:
            _textos_baixados[url] = r.read().decode('utf-8', 'replace').strip()
    return _textos_baixados[url]

NS = {'m': 'http://maven.apache.org/POM/4.0.0'}


def indexar_cache():
    """nome do arquivo -> (grupo, artefato, versão, pasta da versão)"""
    idx = {}
    for grupo in os.listdir(CACHE):
        pg = os.path.join(CACHE, grupo)
        for art in os.listdir(pg):
            pa = os.path.join(pg, art)
            for ver in os.listdir(pa):
                pv = os.path.join(pa, ver)
                for h in os.listdir(pv):
                    for f in os.listdir(os.path.join(pv, h)):
                        idx.setdefault(f, (grupo, art, ver, pv))
    return idx


def pom(pasta_versao, art, ver):
    for h in os.listdir(pasta_versao):
        p = os.path.join(pasta_versao, h, f'{art}-{ver}.pom')
        if os.path.isfile(p):
            return p
    return None


def texto(el, caminho):
    if el is None:
        return None
    x = el.find(caminho, NS)
    if x is None:
        x = el.find(caminho.replace('m:', ''))
    return x.text.strip() if x is not None and x.text else None


def ler_pom(caminho):
    try:
        raiz = ET.parse(caminho).getroot()
    except ET.ParseError:
        return None
    return raiz


def licencas_do_pom(grupo, art, ver, profundidade=0):
    """[(nome, url)], url do projeto, organização; sobe pelos POMs pais"""
    pasta = os.path.join(CACHE, grupo, art, ver)
    if not os.path.isdir(pasta):
        return [], None, None
    p = pom(pasta, art, ver)
    if not p:
        return [], None, None
    raiz = ler_pom(p)
    if raiz is None:
        return [], None, None
    lic = []
    for l in raiz.findall('m:licenses/m:license', NS) + raiz.findall('licenses/license'):
        lic.append((texto(l, 'm:name') or '?', texto(l, 'm:url') or ''))
    url = texto(raiz, 'm:url') or texto(raiz, 'm:scm/m:url')
    org = texto(raiz, 'm:organization/m:name')
    if not lic and profundidade < 6:
        pai = raiz.find('m:parent', NS)
        if pai is not None:
            l2, u2, o2 = licencas_do_pom(texto(pai, 'm:groupId'), texto(pai, 'm:artifactId'), texto(pai, 'm:version'), profundidade + 1)
            lic = l2
            url = url or u2
            org = org or o2
    return lic, url, org


def textos_do_jar(caminho):
    """[(nome do arquivo, texto)] de LICENSE/NOTICE dentro do jar"""
    achados = []
    with zipfile.ZipFile(caminho) as z:
        for n in z.namelist():
            base = n.rsplit('/', 1)[-1].upper()
            if n.endswith('/') or not re.match(r'^(LICEN[CS]E|NOTICE|COPYING)(\.TXT|\.MD)?$', base):
                continue
            if n.count('/') > 2:
                continue
            try:
                achados.append((n, z.read(n).decode('utf-8', 'replace').strip()))
            except KeyError:
                pass
    return achados


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    with zipfile.ZipFile(sys.argv[1]) as apk:
        jars = [l.strip() for l in apk.read('assets/java/classpath.txt').decode().splitlines() if l.strip()]
    idx = indexar_cache()
    apache_padrao = open(os.path.join(os.path.dirname(SAIDA), 'apache-2.0.txt'), encoding='utf-8').read()
    impressao_apache = hashlib.sha256(re.sub(r'\s+', ' ', apache_padrao[:3000]).encode()).hexdigest()

    linhas = [
        'BIBLIOTECAS JAVA NO APK',
        f'Gerado por scripts/gerar-licencas-java.py em {datetime.date.today().isoformat()}.',
        'Cada biblioteca com a licença declarada pelo projeto e os textos de LICENSE e NOTICE que vêm dentro dela.',
        'Textos completos das licenças comuns: Apache-2.0 (apache-2.0.txt), LGPL/GPL v3 (ffmpeg-*.txt), nas licenças do app.',
        '',
    ]
    sem_licenca = []
    for jar in jars:
        if jar in NOSSOS:
            continue
        caminho = next((os.path.join(d, jar) for d in LIBS if os.path.isfile(os.path.join(d, jar))), None)
        if caminho is None and jar in idx:
            caminho = next((os.path.join(idx[jar][3], h, jar) for h in os.listdir(idx[jar][3]) if os.path.isfile(os.path.join(idx[jar][3], h, jar))), None)
        info = idx.get(jar)
        coord = f'{info[0]}:{info[1]}:{info[2]}' if info else '?'
        lic, url, org = licencas_do_pom(info[0], info[1], info[2]) if info else ([], None, None)
        if not lic:
            sem_licenca.append(jar)
        linhas.append('=' * 72)
        linhas.append(f'{jar}  ({coord})')
        for nome, u in lic or [('não declarada no POM', '')]:
            linhas.append(f'Licença: {nome}' + (f' ({u})' if u else ''))
        if url:
            linhas.append(f'Projeto: {url}')
        if org:
            linhas.append(f'Organização: {org}')
        if caminho:
            for nome, t in textos_do_jar(caminho):
                impressao = hashlib.sha256(re.sub(r'\s+', ' ', t[:3000]).encode()).hexdigest()
                if impressao == impressao_apache:
                    linhas.append(f'{nome}: texto padrão da Apache License 2.0 (apache-2.0.txt)')
                    continue
                linhas.append(f'--- {nome} ---')
                linhas.append(t)
        art = info[1] if info else ''
        if art in TEXTO_DO_REPOSITORIO:
            url_texto = TEXTO_DO_REPOSITORIO[art]
            linhas.append(f'--- licença do repositório do projeto ({url_texto}) ---')
            linhas.append(texto_do_repositorio(url_texto))
        elif art.startswith('lwjgl'):
            linhas.append('Texto da licença: lwjgl.txt (nas licenças do app)')
        linhas.append('')

    os.makedirs(os.path.dirname(SAIDA), exist_ok=True)
    with open(SAIDA, 'w', encoding='utf-8', newline='\n') as f:
        f.write('\n'.join(linhas) + '\n')
    print(f'{len(jars) - len(NOSSOS)} bibliotecas -> {SAIDA}')
    if sem_licenca:
        print('SEM LICENÇA IDENTIFICADA:', ', '.join(sem_licenca))


if __name__ == '__main__':
    main()
