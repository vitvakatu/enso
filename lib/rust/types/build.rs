//! Generation of Dim* macros, macros allowing generation of swizzling getters and setters.

// === Non-Standard Linter Configuration ===
#![allow(clippy::bool_to_int_with_if)]
#![allow(clippy::let_and_return)]
#![allow(clippy::option_map_unit_fn)]
#![allow(clippy::precedence)]
#![allow(dead_code)]
#![deny(non_ascii_idents)]
#![deny(unconditional_recursion)]
#![warn(unsafe_code)]
#![warn(missing_copy_implementations)]
#![warn(missing_debug_implementations)]
#![warn(missing_docs)]
#![warn(trivial_casts)]
#![warn(trivial_numeric_casts)]
#![warn(unused_import_braces)]
#![warn(unused_qualifications)]

use enso_prelude::*;

use std::fmt::Write;
use std::fs::File;
use std::io::Write as IoWrite;



// =================
// === Constants ===
// =================

const FILE: &str = "src/dim_macros.rs";
const AXES: &[&str] = &["x", "y", "z", "w"];
const INDENT_SIZE: usize = 4;



// ========================
// === Formatting Utils ===
// ========================

fn indent(level: usize) -> String {
    " ".repeat(level * INDENT_SIZE)
}



// ======================
// === Implementation ===
// ======================

/// Generates swizzling data. The [`base_dim`] describes what dimension the data should be generated
/// in. For example, if it is set to 3, all of "x", "y", and "z" axes will be combined to generate
/// the result. The [`dim`] describes the dimension of the swizzling. For example, if it is set to
/// 2, the result will contain 2-dimensional coordinates, like "xy", or "yz". If the [`unique`] flag
/// is set, the generated swizzling will not have repeated axes (e.g. `xx` is not allowed).
///
/// The output is a vector of four elements:
/// - The swizzling name, like "xy", "xyz", "xz", etc.
/// - The swizzling dimension.
/// - The swizzling component indexes. For example [0, 2] for "xz".
/// - The enumeration of components. For example [0, 1] for "xz".
///
/// For example, for the base dimension of 3 and the dimension of 2, the following swizzles will be
/// generated:
///
/// ```text
/// xz 2 [0, 2] [0, 1]
/// yz 2 [1, 2] [0, 1]
/// zx 2 [2, 0] [0, 1]
/// zy 2 [2, 1] [0, 1]
/// zz 2 [2, 2] [0, 1]
/// ```
///
/// See the generated [`FILE`] to see the result of this script.
fn gen_swizzling(
    base_dim: usize,
    dim: usize,
    unique: bool,
) -> Vec<(Vec<String>, usize, Vec<usize>, Vec<usize>)> {
    let mut vec: Vec<(Vec<String>, Vec<usize>, Vec<usize>)> = vec![(vec![], vec![], vec![])];
    for _ in 0..dim {
        vec = vec
            .clone()
            .into_iter()
            .cartesian_product(AXES[0..base_dim].iter().enumerate())
            .filter_map(|((mut prod, mut ixs, mut ord), (ix, axis))| {
                if unique && ixs.contains(&ix) {
                    return None;
                }
                prod.push(axis.to_string());
                ixs.push(ix);
                ord.push(ord.len());
                Some((prod, ixs, ord))
            })
            .collect_vec();
    }
    vec.into_iter().map(|(prod, ixs, ord)| (prod, dim, ixs, ord)).collect_vec()
}

/// Just like [`gen_swizzling`], but the output always contains the dimension component. For
/// example, if the dimension was set to 2, the output will contain all swizzling combinations that
/// contain the "z" component.
fn gen_swizzling_force_dim_component(
    input_dim: usize,
    dim: usize,
    unique: bool,
) -> Vec<(Vec<String>, usize, Vec<usize>, Vec<usize>)> {
    let axe = AXES[input_dim - 1];
    gen_swizzling(input_dim, dim, unique)
        .into_iter()
        .filter(|(axes, _, _, _)| axes.contains(&axe.to_string()))
        .collect()
}

fn gen_swizzling_macro_branch(input_dim: usize, unique: bool) -> String {
    let mut out = String::new();
    out.write_str(&format!(
        "{}({}, $f: ident $(,$($args:tt)*)?) => {{ $f! {{ $([$($args)*])? {}\n",
        indent(1),
        input_dim,
        input_dim,
    ))
    .unwrap();

    for dim in 1..input_dim {
        for (axes, dim, ixs, ord) in gen_swizzling_force_dim_component(input_dim, dim, unique) {
            out.write_str(&format!("{}{} {} {:?} {:?}\n", indent(2), axes.join(""), dim, ixs, ord))
                .unwrap();
        }
    }
    for (axes, dim, ixs, ord) in gen_swizzling(input_dim, input_dim, unique) {
        out.write_str(&format!("{}{} {} {:?} {:?}\n", indent(2), axes.join(""), dim, ixs, ord))
            .unwrap();
    }
    out.write_str(&format!("{}}}}};\n", indent(1))).unwrap();
    out
}

/// The generated macro accepts two arguments, the dimension of the swizzling and another macro name
/// that should be called with the swizzling data. The provided macro will be called with the chosen
/// dimension and the data generated by the [`gen_swizzling_macro_branch`] function.
///
/// See the generated [`FILE`] to see the result of this script.
fn gen_swizzling_macro(unique: bool) -> String {
    let mut out = String::new();
    let sfx = if unique { "_unique" } else { "" };
    out.write_str("/// Swizzling data for the given dimension.\n").unwrap();
    out.write_str("/// See the [`build.rs`] file to learn more.\n").unwrap();
    out.write_str("#[macro_export]\n").unwrap();
    out.write_str(&format!("macro_rules! with_swizzling_for_dim{} {{\n", sfx)).unwrap();
    out.write_str(&gen_swizzling_macro_branch(1, unique)).unwrap();
    out.write_str(&gen_swizzling_macro_branch(2, unique)).unwrap();
    out.write_str(&gen_swizzling_macro_branch(3, unique)).unwrap();
    out.write_str(&gen_swizzling_macro_branch(4, unique)).unwrap();
    out.write_str("}").unwrap();
    out
}

fn main() {
    println!("cargo:rerun-if-changed=build.rs");
    let mut file = File::create(FILE).unwrap();
    let warning = "THIS IS AN AUTO-GENERATED FILE. DO NOT EDIT IT DIRECTLY!";
    let border = "!".repeat(warning.len());

    let mut out = String::new();
    out.write_str("//! Macros allowing generation of swizzling getters and setters.\n").unwrap();
    out.write_str("//! See the docs of [`build.rs`] and usage places to learn more.\n").unwrap();
    out.write_str(&format!("\n// {}\n", border)).unwrap();
    out.write_str("// THIS IS AN AUTO-GENERATED FILE. DO NOT EDIT IT DIRECTLY!\n").unwrap();
    out.write_str(&format!("// {}\n\n\n", border)).unwrap();
    out.write_str(&gen_swizzling_macro(false)).unwrap();
    out.write_str("\n\n").unwrap();
    out.write_str(&gen_swizzling_macro(true)).unwrap();
    out.write_str("\n").unwrap();
    file.write_all(out.as_bytes()).unwrap();
}