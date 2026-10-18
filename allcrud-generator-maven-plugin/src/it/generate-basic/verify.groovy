def root = new File(basedir, 'src/main/java/org/openapitools')

def generated = [
    'api/GlobalExceptionHandler.java',
    'api/ProductRepository.java',
    'api/ProductConverter.java',
    'api/OrderRepository.java',
    'api/OrderService.java',
]
generated.each { assert new File(root, it).isFile() : "missing generated file $it" }

def absent = [
    'model/OrderVO.java',
    'api/OrderConverter.java',
    'api/OrderController.java',
]
absent.each { assert !new File(root, it).exists() : "unexpected file $it" }

def handWritten = [
    'api/ProductService.java'   : 'package org.openapitools.api;\n\npublic class ProductService {\n\n    public String handWritten() {\n        return "kept";\n    }\n\n}\n',
    'api/ProductController.java': 'package org.openapitools.api;\n\npublic class ProductController {\n\n    public String handWritten() {\n        return "kept";\n    }\n\n}\n',
    'model/ProductVO.java'      : 'package org.openapitools.model;\n\npublic class ProductVO {\n\n    public String handWritten() {\n        return "kept";\n    }\n\n}\n',
]
handWritten.each { path, content ->
    assert new File(root, path).text == content : "$path was overwritten"
}

def log = new File(basedir, 'build.log').text
assert (log =~ /BUILD SUCCESS/).count == 2 : 'expected two successful generator executions'

return true
