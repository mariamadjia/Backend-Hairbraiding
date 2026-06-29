# Deploying to Render

This guide walks you through deploying the AH Braiding backend to Render.

## Prerequisites

- GitHub account with this repository pushed
- Render account (free tier available)

## Step 1: Create PostgreSQL Database on Render

1. Go to [Render Dashboard](https://dashboard.render.com/)
2. Click **New +** → **PostgreSQL**
3. Configure:
   - **Name**: `braiding-db` (or your choice)
   - **Database**: `braiding`
   - **User**: `braiding_user` (auto-generated)
   - **Region**: Choose closest to your users
   - **Plan**: Free or paid
4. Click **Create Database**
5. **Save the connection details** - you'll need them for the web service

## Step 2: Create Web Service on Render

1. Click **New +** → **Web Service**
2. Connect your GitHub repository
3. Configure:
   - **Name**: `ah-braiding-backend`
   - **Region**: Same as database
   - **Branch**: `main`
   - **Root Directory**: Leave empty
   - **Runtime**: `Java`
   - **Build Command**: `./mvnw clean package -DskipTests`
   - **Start Command**: `java -jar target/Backend-Braiding-0.0.1-SNAPSHOT.jar`
   - **Plan**: Free or paid

## Step 3: Configure Environment Variables

In the Render dashboard for your web service, go to **Environment** and add these variables:

### Database (from Step 1)
```
DATABASE_URL=<Internal Database URL from Render PostgreSQL>
DATABASE_USERNAME=<Database User>
DATABASE_PASSWORD=<Database Password>
```

**Note**: Render provides an "Internal Database URL" - use that instead of the external one for better performance.

### JWT Authentication
```
JWT_SECRET=5367566B59703373367639792F423F4528482B4D6251655468576D5A71347437
```

### Vonage SMS
```
VONAGE_API_KEY=3a59a511
VONAGE_API_SECRET=85S4x9bWz0Lh4MAv
VONAGE_PHONE_NUMBER=19802705789
```

### CORS (Update with your frontend URL)
```
CORS_ALLOWED_ORIGINS=https://your-frontend-app.vercel.app,https://your-custom-domain.com
```

### Stripe (Optional - add when ready)
```
STRIPE_SECRET_KEY=sk_live_your_live_key
STRIPE_PUBLIC_KEY=pk_live_your_live_key
STRIPE_WEBHOOK_SECRET=whsec_your_webhook_secret
```

### Email (Optional - add when ready)
```
EMAIL_USERNAME=your-email@gmail.com
EMAIL_PASSWORD=your_app_specific_password
```

## Step 4: Deploy

1. Click **Create Web Service**
2. Render will automatically:
   - Clone your repository
   - Run the build command
   - Start your application
3. Monitor the logs for any errors

## Step 5: Update Frontend

Update your frontend's API URL to point to your Render backend:

```typescript
// In your Next.js frontend
const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'https://ah-braiding-backend.onrender.com';
```

Add this to your frontend's `.env.production`:
```
NEXT_PUBLIC_API_URL=https://ah-braiding-backend.onrender.com
```

## Important Notes

### Free Tier Limitations
- ⚠️ **Spins down after 15 minutes of inactivity**
- First request after spin-down takes 30-60 seconds
- 750 hours/month free (enough for one service)

### Database Backups
- Free tier: No automatic backups
- Paid tier: Daily backups included
- **Recommendation**: Export data regularly

### File Uploads
- Files uploaded to `/public/` will be **lost on redeploy**
- **Solution**: Use cloud storage (AWS S3, Cloudinary, etc.)

### Logs
- Access logs from Render dashboard
- Free tier: Limited log retention

## Troubleshooting

### Build Fails
```bash
# Check if mvnw has execute permissions
git update-index --chmod=+x mvnw
git commit -m "Make mvnw executable"
git push
```

### Database Connection Issues
- Verify DATABASE_URL is the **Internal Database URL**
- Check database is in the same region as web service
- Ensure database is running

### CORS Errors
- Add your frontend URL to CORS_ALLOWED_ORIGINS
- Include both `http://` and `https://` if needed
- Don't forget trailing slashes if your frontend uses them

### Application Won't Start
- Check logs in Render dashboard
- Verify all required environment variables are set
- Ensure port 8080 is used (Render expects this)

## Monitoring

- **Health Check**: Render automatically monitors your service
- **Custom Health Endpoint**: Consider adding `/actuator/health` with Spring Boot Actuator

## Scaling

To upgrade from free tier:
1. Go to your service settings
2. Change plan to Starter ($7/month) or higher
3. Benefits:
   - No spin-down
   - More resources
   - Better performance

## Your Backend URL

After deployment, your backend will be available at:
```
https://ah-braiding-backend.onrender.com
```

(Replace with your actual Render service name)
