/**
 * @jenesis.pin maven/org.apache.commons/commons-lang3/pom 3.21.0 SHA-256/9420ac3c5efb5bcf804552a7bec00a2d857120977fc12151f7606a67ba7a184d
 * @jenesis.pin maven/org.apache.commons/commons-parent/pom 105 SHA-256/31a68daabb4b361453788b9045ac87a6ae87c705e578facd99e4d9ddb6a481e9
 * @jenesis.pin maven/org.apache/apache/pom 39 SHA-256/71752ed4c2435a1d8385fe0a4d330f39d04efec89f0d1ba5d1598d39f7397a44
 * @jenesis.pin maven/org.junit/junit-bom/pom 5.14.4 SHA-256/5706e8f29a0a07f56efbbea4a0670793414194bb8d24d8143ba1e787a2f32856
 * @jenesis.pin maven/org.mockito/mockito-bom/pom 4.11.0 SHA-256/d8531a746c988f7f68ed5f188cdea945006aea993ec5df9e524e0d27d61491da
 * @jenesis.pin org.apache.commons.lang3 3.21.0 SHA-256/a2af247ec4db6eb8947e18e23e0433e4871a7c5d0a40e1f92e46ede54ae1defe
 * @jenesis.pin org.apache.commons/commons-lang3 3.21.0 SHA-256/a2af247ec4db6eb8947e18e23e0433e4871a7c5d0a40e1f92e46ede54ae1defe
 */
module demo.profiles {
    requires org.apache.commons.lang3;
    exports profiles;
}
