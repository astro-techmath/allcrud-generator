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

def log = new File(basedir, 'build.log').text
assert (log =~ /--- allcrud-generator:[^ ]+:generate/).count == 2 : 'expected the generate goal in both executions'
assert (log =~ /--- compiler:[^ ]+:compile/).count == 2 : 'expected the compile goal in both executions'
assert (log =~ /BUILD SUCCESS/).count == 2 : 'expected both executions to succeed'

return true
