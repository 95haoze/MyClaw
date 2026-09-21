import DOMPurify from 'dompurify'
import {marked} from 'marked'
import hljs from 'highlight.js/lib/core'
import bash from 'highlight.js/lib/languages/bash'
import css from 'highlight.js/lib/languages/css'
import java from 'highlight.js/lib/languages/java'
import javascript from 'highlight.js/lib/languages/javascript'
import json from 'highlight.js/lib/languages/json'
import markdown from 'highlight.js/lib/languages/markdown'
import python from 'highlight.js/lib/languages/python'
import sql from 'highlight.js/lib/languages/sql'
import typescript from 'highlight.js/lib/languages/typescript'
import xml from 'highlight.js/lib/languages/xml'
import yaml from 'highlight.js/lib/languages/yaml'

hljs.registerLanguage('bash', bash)
hljs.registerLanguage('shell', bash)
hljs.registerLanguage('css', css)
hljs.registerLanguage('java', java)
hljs.registerLanguage('javascript', javascript)
hljs.registerLanguage('js', javascript)
hljs.registerLanguage('json', json)
hljs.registerLanguage('markdown', markdown)
hljs.registerLanguage('md', markdown)
hljs.registerLanguage('python', python)
hljs.registerLanguage('py', python)
hljs.registerLanguage('sql', sql)
hljs.registerLanguage('typescript', typescript)
hljs.registerLanguage('ts', typescript)
hljs.registerLanguage('html', xml)
hljs.registerLanguage('xml', xml)
hljs.registerLanguage('vue', xml)
hljs.registerLanguage('yaml', yaml)
hljs.registerLanguage('yml', yaml)

const renderer = new marked.Renderer()
renderer.code = ({text, lang}) => {
    const requested = (lang || '').trim().split(/\s+/)[0]!.toLowerCase()
    const known = requested && hljs.getLanguage(requested)
    const highlighted = known ? hljs.highlight(text, {language: requested}).value : hljs.highlightAuto(text).value
    const label = requested || 'text'
    return `<div class="code-block"><div class="code-toolbar"><span>${label}</span><button type="button" data-copy-code aria-label="复制代码">复制</button></div><pre><code class="hljs language-${label}">${highlighted}</code></pre></div>`
}
marked.setOptions({gfm: true, breaks: true, renderer})

export function renderMarkdown(source: string): string {
    const html = marked.parse(source, {async: false})
    return DOMPurify.sanitize(html, {ADD_ATTR: ['data-copy-code']})
}
