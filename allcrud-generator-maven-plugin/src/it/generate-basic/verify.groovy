def root = new File(basedir, 'src/main/java/org/openapitools')
def classes = new File(basedir, 'target/classes/org/openapitools')

def generated = [
    'api/GlobalExceptionHandler',
    'api/ProductRepository',
    'api/ProductConverter',
    'api/ProductService',
    'api/ProductController',
    'api/OrderRepository',
    'api/OrderService',
    'model/ProductVO',
]
generated.each {
    assert new File(root, "${it}.java").isFile() : "missing generated file ${it}.java"
    assert new File(classes, "${it}.class").isFile() : "missing compiled class ${it}.class"
}

def absent = [
    'model/OrderVO.java',
    'api/OrderConverter.java',
    'api/OrderController.java',
]
absent.each { assert !new File(root, it).exists() : "unexpected file $it" }

def stamp = 1000000000000L
['src/main/java', 'src/test/java'].collect { new File(basedir, it) }.findAll { it.isDirectory() }.each { dir ->
    dir.eachFileRecurse(groovy.io.FileType.FILES) { assert it.setLastModified(stamp) : "could not stamp $it" }
}

def log = new File(basedir, 'build.log').text
assert log.contains('--- allcrud-generator:') && log.contains(':generate ') : 'expected the generate goal'
assert (log =~ /--- compiler:[^ ]+:compile/).find() : 'expected the compile goal'

return true
