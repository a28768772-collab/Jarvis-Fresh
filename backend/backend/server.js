
const express = require("express");
const helmet = require("helmet");
const rateLimit = require("express-rate-limit");
const crypto = require("node:crypto");
const { GoogleGenAI } = require("@google/genai");

require("dotenv").config();

const app = express();

app.disable("x-powered-by");
app.use(helmet());
app.use(express.json({ limit: "16kb" }));

// Limit repeated requests.
app.use(
  rateLimit({
    windowMs: 60 * 1000,
    limit: 20,
    standardHeaders: "draft-7",
    legacyHeaders: false
  })
);

const GEMINI_API_KEY = process.env.GEMINI_API_KEY;
const JARVIS_ACCESS_TOKEN = process.env.JARVIS_ACCESS_TOKEN;

if (!GEMINI_API_KEY || !JARVIS_ACCESS_TOKEN) {
  console.error(
    "Missing GEMINI_API_KEY or JARVIS_ACCESS_TOKEN"
  );
  process.exit(1);
}

const ai = new GoogleGenAI({
  apiKey: GEMINI_API_KEY
});

// Check that the supplied token matches.
function authenticate(req, res, next) {
  const authorization = req.get("authorization") || "";

  if (!authorization.startsWith("Bearer ")) {
    return res.status(401).json({
      error: "Unauthorized"
    });
  }

  const suppliedToken = authorization.slice(7).trim();

  const expected = Buffer.from(JARVIS_ACCESS_TOKEN);
  const supplied = Buffer.from(suppliedToken);

  if (
    expected.length !== supplied.length ||
    !crypto.timingSafeEqual(expected, supplied)
  ) {
    return res.status(401).json({
      error: "Unauthorized"
    });
  }

  next();
}

// Public health check. Does not expose secrets.
app.get("/health", (req, res) => {
  res.json({
    status: "ok",
    assistant: "Jarvis",
    message: "Backend server is running"
  });
});

// Protected Gemini chat endpoint.
app.post("/chat", authenticate, async (req, res) => {
  try {
    const message = req.body?.message;

    if (
      typeof message !== "string" ||
      !message.trim() ||
      message.length > 4000
    ) {
      return res.status(400).json({
        error: "Please provide a valid message"
      });
    }

    const response = await ai.models.generateContent({
      model: "gemini-2.5-flash",
      contents: message.trim(),
      config: {
        systemInstruction: `
You are Jarvis, a helpful personal AI assistant.

Rules:
- Reply primarily in natural Hindi.
- Be concise, clear, polite and useful.
- Address the user as "सर" when appropriate.
- Never claim that you have controlled the phone
  or performed an action unless the Android app
  has actually confirmed that action.
- Do not request passwords, API keys or secrets.
- You provide conversational answers.
- Android device actions must be performed
  separately by authorized Android application code.
        `
      }
    });

    const reply = response.text?.trim();

    if (!reply) {
      return res.status(502).json({
        error: "Gemini returned an empty response"
      });
    }

    return res.json({
      assistant: "Jarvis",
      reply
    });

  } catch (error) {
    console.error("Gemini request failed:", {
      status: error.status || null,
      name: error.name || "Error"
    });

    return res.status(502).json({
      error: "AI service is temporarily unavailable"
    });
  }
});

// Handle invalid JSON.
app.use((error, req, res, next) => {
  if (error instanceof SyntaxError && "body" in error) {
    return res.status(400).json({
      error: "Invalid JSON"
    });
  }

  next(error);
});

app.use((req, res) => {
  res.status(404).json({
    error: "Route not found"
  });
});

const PORT = process.env.PORT || 3000;

app.listen(PORT, "0.0.0.0", () => {
  console.log(`Jarvis backend running on port ${PORT}`);
});
