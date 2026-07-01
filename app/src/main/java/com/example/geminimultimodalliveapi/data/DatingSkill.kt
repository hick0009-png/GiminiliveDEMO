package com.example.geminimultimodalliveapi.data

data class DatingSkill(
    val id: String,          // Filename e.g., "sweet_flirting"
    val name: String,        // Frontmatter name
    val description: String, // Frontmatter description
    val instructions: String // Markdown body instructions
)
