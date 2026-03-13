#[uniffi::export]
pub fn greet(name: String) -> String {
    format!("Hello, {}! Welcome to Rust.", name)
}

#[uniffi::export]
pub fn add(left: u64, right: u64) -> u64 {
    left + right
}

#[derive(uniffi::Record)]
pub struct Person {
    pub name: String,
    pub age: u32,
}

#[uniffi::export]
pub fn create_person(name: String, age: u32) -> Person {
    Person { name, age }
}

uniffi::setup_scaffolding!();

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn it_works() {
        let result = add(2, 2);
        assert_eq!(result, 4);
    }
}
